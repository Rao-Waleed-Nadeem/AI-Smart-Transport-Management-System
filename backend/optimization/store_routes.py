# optimization/store_routes.py
import math
import time
import requests
from deep_translator import GoogleTranslator  # pip install deep-translator
from firebase_admin import firestore
from database.firebase import get_collection
from optimization.tsp_solver import solve_tsp
from optimization.route_generator import generate_route_path
from config.settings import (
    UNIVERSITY_COORD,
    FUEL_PRICE_PER_LITER,
    BUS_FUEL_EFFICIENCY_KM_PER_LITER,
    PROFIT_MARGIN_PERCENT,
    SEMESTER_MONTHS,
    DAYS_PER_MONTH
)
from config.logging_config import logger

def compute_fee_per_student(total_distance_km: float, total_students: int) -> float:
    """Calculate per-student fee for 4-month semester (adjustable via .env)"""
    if total_students <= 0:
        return 0.0

    fuel_cost_per_km = FUEL_PRICE_PER_LITER / BUS_FUEL_EFFICIENCY_KM_PER_LITER
    total_active_days = DAYS_PER_MONTH * SEMESTER_MONTHS
    total_fuel_cost = fuel_cost_per_km * total_distance_km * total_active_days
    base_cost_per_student = total_fuel_cost / total_students
    profit = base_cost_per_student * (PROFIT_MARGIN_PERCENT / 100)
    final_fee = math.ceil(base_cost_per_student + profit)

    logger.info(f"Fee calc: {final_fee} PKR/student | dist={total_distance_km:.1f}km | students={total_students}")
    return final_fee


def _haversine_km(lat1: float, lng1: float, lat2: float, lng2: float) -> float:
    """Straight-line distance in km between two lat/lng points (Haversine)."""
    import math
    R = 6371.0
    d_lat = math.radians(lat2 - lat1)
    d_lng = math.radians(lng2 - lng1)
    a = (math.sin(d_lat / 2) ** 2 +
         math.cos(math.radians(lat1)) * math.cos(math.radians(lat2)) *
         math.sin(d_lng / 2) ** 2)
    return R * 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))


def _to_english(text: str) -> str:
    """Translate text to English using Google Translate (via deep-translator).

    Only translates if the string contains non-ASCII characters (i.e. Urdu/Arabic
    script). Pure ASCII strings are returned immediately — no network call needed.

    deep-translator wraps Google Translate for free without an API key.
    Install: pip install deep-translator

    Args:
        text: The string to translate (may be Urdu, Arabic, or already English).

    Returns:
        English translation, or the original string if translation fails.
    """
    # Fast path — already ASCII / English, nothing to do
    if all(c.isascii() for c in text):
        return text
    try:
        translated = GoogleTranslator(source='auto', target='en').translate(text)
        # GoogleTranslator returns None on empty input; guard against it
        return translated.strip() if translated else text
    except Exception as e:
        logger.warning(f"Translation failed for '{text}': {e} — using original.")
        return text


def reverse_geocode_stop_name(lat: float, lng: float) -> str:
    """Fetch a clean English place name for a lat/lng using Nominatim + Google Translate.

    Two-step process:
        1. Nominatim (OSM) reverse geocode with accept-language=en — gets English
           names where OSM data has them.
        2. Any component still in Urdu/Arabic (non-ASCII) is passed through
           _to_english() which uses Google Translate (deep-translator, free, no key)
           to convert it to English before it is stored.

    This guarantees that regardless of what language OSM contributors used when
    entering road/suburb names, the stored stop_name is always English.

    Args:
        lat: Latitude of the stop.
        lng: Longitude of the stop.

    Returns:
        A clean, English-only place name string.
    """
    url = "https://nominatim.openstreetmap.org/reverse"
    params = {
        'lat': lat,
        'lon': lng,
        'format': 'jsonv2',
        'addressdetails': 1,
        'accept-language': 'en',   # request English first; translate remainder below
    }
    headers = {'User-Agent': 'AISmartTransportSystem/1.0'}

    try:
        resp = requests.get(url, params=params, headers=headers, timeout=10)
        resp.raise_for_status()
        data = resp.json()
    except Exception as e:
        logger.warning(f"Reverse geocode failed for ({lat}, {lng}): {e}")
        return f"Stop ({lat:.4f}, {lng:.4f})"

    address = data.get('address', {})

    def clean(text: str) -> str:
        """Strip whitespace and vague/useless prefixes, then translate to English."""
        text = text.strip()
        # Remove prefixes that carry no real location information
        for junk in ('unknown ', 'unnamed ', 'unnamed road', 'unnamed street'):
            if text.lower().startswith(junk):
                text = text[len(junk):].strip()
        # Translate any remaining non-ASCII (Urdu/Arabic) content to English
        text = _to_english(text)
        return text.strip()

    # Full OSM address key priority — ordered from most to least specific.
    # Covers urban streets, rural villages, hamlets, and everything in between.
    # Any non-empty value that survives clean() is accepted (Urdu translated above).
    priority_keys = [
        # ── Street-level (most specific) ──────────────────────────────────────
        'road',
        'pedestrian',
        'cycleway',
        'footway',
        'path',
        'track',
        # ── Named locality below city ──────────────────────────────────────────
        'suburb',
        'neighbourhood',
        'quarter',
        'residential',
        # ── Village / hamlet (common in peri-urban / rural OSM data) ──────────
        'village',
        'hamlet',
        'isolated_dwelling',
        'locality',
        'allotments',
        # ── City / district ────────────────────────────────────────────────────
        'city_district',
        'district',
        'borough',
        'municipality',
        'town',
        'city',
        # ── County / region (last resort before coordinates) ──────────────────
        'county',
        'state_district',
    ]

    parts = []
    for key in priority_keys:
        val = address.get(key, '')
        if val:
            val = clean(val)
            if val:
                parts.append(val)
                break  # take only the single best (most specific) component

    # Always append city/town for context if not already the chosen component
    context = clean(address.get('city', '') or address.get('town', '') or address.get('municipality', ''))
    if context and context not in parts:
        parts.append(context)

    if parts:
        result = ', '.join(parts)
        logger.debug(f"Stop name resolved: ({lat:.4f}, {lng:.4f}) → '{result}'")
        return result

    # display_name last-resort: take first two comma-separated parts of Nominatim's
    # own display_name string (already English-biased) and translate what remains.
    display = data.get('display_name', '')
    if display:
        segments = [s.strip() for s in display.split(',') if s.strip()][:2]
        translated = [clean(s) for s in segments if s]
        if translated:
            result = ', '.join(translated)
            logger.debug(f"Stop name from display_name: ({lat:.4f}, {lng:.4f}) → '{result}'")
            return result

    logger.warning(f"No usable address for ({lat}, {lng}), using coordinates.")
    return f"Stop ({lat:.4f}, {lng:.4f})"

def store_optimized_routes(bus_clusters):
    """
    Stores optimized routes and stops in Firestore with clean, flat structure.

    Features:
    - route_name: "R1", "R2", etc.
    - seat_number per student: "S1", "S2", ... assigned in stop order, stored on
      each student document (not as an array on the route)
    - stop_name: real English address from Nominatim reverse geocoding (OSM)
    - bus_id on route only (not on stop)
    - route_name on route only (not on stop)
    - fee_per_student_pkr on each stop (not on route)
    - stop_id / route_id are Firestore document IDs; not stored as explicit fields
    - Null instead of 0 for unset numeric fields
    - Geometry flattened as list of {"lng": x, "lat": y} maps (Firestore-safe)
    - All fields flat → no "Nested arrays are not allowed" error
    """
    routes_col = get_collection('routes')
    stops_col   = get_collection('stops')
    fees_col    = get_collection('fees')
    students_col = get_collection('students')

    route_to_stop_ids = {}  # For back-filling route_id

    for bus_id, cluster_stops in bus_clusters.items():
        total_students = sum(s['student_count'] for s in cluster_stops)

        # TSP ordering
        ordered_stops = solve_tsp(cluster_stops, UNIVERSITY_COORD)
        if not ordered_stops:
            logger.warning(f"Bus {bus_id} skipped: TSP failed")
            continue

        # Generate actual route path (ORS)
        geometry, distance_km, duration_h = generate_route_path(ordered_stops, UNIVERSITY_COORD)
        if not geometry:
            logger.warning(f"Bus {bus_id} skipped: route generation failed")
            continue

        # Fee
        fee_per_student = compute_fee_per_student(distance_km, total_students)

        # Route name: R1, R2, ...
        route_name = f"R{bus_id + 1}"

        # Seat numbers assigned per student across stops: S1, S2, S3...
        # Each student gets their seat_number written to their own document below.
        seat_counter = 1   # increments across all stops in this route

        # Collect stop IDs and store stops
        stop_ids = []
        optimized_order_stop_ids = []

        for idx, stop in enumerate(ordered_stops):
            stop_id = stop.get('stop_id') or f"{route_name}_stop{idx+1}"
            stop_ids.append(stop_id)
            optimized_order_stop_ids.append(stop_id)

            # ── Real English stop name via Nominatim reverse geocoding ─────────
            # Rate-limit: Nominatim allows 1 request/second for free use.
            if idx > 0:
                time.sleep(1)
            stop_name = reverse_geocode_stop_name(stop['lat'], stop['lng'])

            # ── Assign seat numbers to each student at this stop ──────────────
            # Seat numbers are "S1", "S2", ... assigned sequentially across all
            # stops of this route and written onto each student's own document.
            student_ids = stop.get('merged_student_ids', [stop.get('student_id', 'unknown')])
            students_col = get_collection('students')
            for student_id in student_ids:
                seat_label = f"S{seat_counter}"
                students_col.document(student_id).update({
                    'seat_number': seat_label,
                    'stop_id': stop_id,  # the stop this student belongs to
                    'route_id': None,   # back-filled in Phase 2
                    'updated_at': firestore.SERVER_TIMESTAMP,
                })
                logger.debug(f"Assigned seat {seat_label} to student {student_id}")
                seat_counter += 1

            # ── Stop document (flat) ──────────────────────────────────────────
            # bus_id, route_name are on the route document only (not here).
            # stop_id is the Firestore document ID — not stored as a field.
            # fee_per_student_pkr is stored here so each stop carries cost info.
            stop_doc = {
                'route_id': None,           # filled in Phase 2
                'latitude': stop['lat'],
                'longitude': stop['lng'],
                'student_ids': student_ids,
                'student_count': stop['student_count'],
                'stop_name': stop_name,     # real English address from OSM
                'fee_per_student_pkr': fee_per_student if fee_per_student is not None else None,
                'snap_distance_m': stop.get('snap_distance_m') or None,
                'distance_to_university_km': round(
                    _haversine_km(stop['lat'], stop['lng'],
                                  UNIVERSITY_COORD[0], UNIVERSITY_COORD[1]), 2
                ),
                'is_merged': len(student_ids) > 1,
                'created_at': firestore.SERVER_TIMESTAMP,
                'updated_at': firestore.SERVER_TIMESTAMP
            }

            stops_col.document(stop_id).set(stop_doc, merge=True)
            logger.debug(f"Stored stop {stop_id} | name='{stop_name}' | route={route_name}")

        # Route document (flat)
        # route_id is the Firestore document ID — not stored as an explicit field.
        # fee_per_student_pkr lives on each stop document, not here.
        # seat_numbers are on each student document, not here.
        route_id = f"route_{bus_id}"
        route_doc = {
            'route_name': route_name,                   # R1, R2...
            'bus_id': None,
            'supervisor_id': None,
            'stop_ids': stop_ids,
            'optimized_order': optimized_order_stop_ids,
            'total_distance_km': round(distance_km, 2) if distance_km is not None else None,
            'estimated_time_hours': round(duration_h, 2) if duration_h is not None else None,
            'geometry': [
                {"lng": pt[0], "lat": pt[1]} for pt in geometry
            ] if geometry else None,                    # flattened → no nested arrays
            'total_students': total_students,
            'num_stops': len(ordered_stops),
            'created_at': firestore.SERVER_TIMESTAMP,
            'updated_at': firestore.SERVER_TIMESTAMP
        }

        routes_col.document(route_id).set(route_doc)
        logger.info(f"Stored route {route_id} ({route_name}): {len(ordered_stops)} stops, {distance_km:.2f} km")

        # Remember for back-fill
        route_to_stop_ids[route_id] = stop_ids

    # Phase 2: Back-fill route_id on all stops and student documents (atomic batches)
    logger.info(f"Back-filling route_id on {sum(len(v) for v in route_to_stop_ids.values())} stops + students...")
    db = firestore.client()
    students_col = get_collection('students')
    batch = db.batch()
    write_count = 0

    def flush_batch_if_needed():
        nonlocal batch, write_count
        if write_count >= 499:
            batch.commit()
            logger.debug(f"Batch committed: {write_count} writes")
            batch = db.batch()
            write_count = 0

    for route_id, stop_ids in route_to_stop_ids.items():
        for stop_id in stop_ids:
            # Back-fill route_id on stop document
            batch.update(stops_col.document(stop_id), {"route_id": route_id})
            write_count += 1
            flush_batch_if_needed()

            # Back-fill route_id on each student document assigned to this stop
            stop_snap = stops_col.document(stop_id).get()
            if stop_snap.exists:
                for student_id in stop_snap.to_dict().get('student_ids', []):
                    batch.update(students_col.document(student_id), {"route_id": route_id})
                    write_count += 1
                    flush_batch_if_needed()

    if write_count > 0:
        batch.commit()
        logger.debug(f"Final batch: {write_count} writes")
        
    # ── Phase 3: Store per-student fees ─────────────────────────────────────
    logger.info("Phase 3: Storing per-student fees...")

    total_fees_stored = 0

    for bus_id, cluster_stops in bus_clusters.items():
        route_name = f"R{bus_id + 1}"
        route_id = f"route_{bus_id}"

        # Get route distance (from the stored route doc — safe fallback)
        route_ref = routes_col.document(route_id)
        route_data = route_ref.get().to_dict() or {}
        route_distance_km = route_data.get('total_distance_km', 0.0)

        # Per-bus fee (already calculated earlier — reuse)
        bus_fee_per_student = compute_fee_per_student(route_distance_km, sum(s['student_count'] for s in cluster_stops))

        for stop in cluster_stops:
            student_ids = stop.get('merged_student_ids', [stop.get('student_id', 'unknown')])
            student_count_here = len(student_ids)

            if student_count_here == 0:
                continue

            # Approximate per-student distance (route distance / students in this stop)
            per_student_distance_km = route_distance_km / student_count_here if student_count_here > 0 else 0.0

            for student_id in student_ids:
                fee_doc = {
                    'student_id': student_id,
                    'semester': "Spring 2026",
                    'amount': bus_fee_per_student,  # same fee for all in bus (fair distribution)
                    'payment_status': "unpaid",
                    'route_distance_km': round(per_student_distance_km, 2),
                    'created_at': firestore.SERVER_TIMESTAMP,
                    'updated_at': firestore.SERVER_TIMESTAMP
                }

                fee_id = student_id  # Use student_id as fee document ID for easy lookup
                fees_col.document(fee_id).set(fee_doc, merge=True)
                total_fees_stored += 1
                logger.debug(f"Stored fee for {student_id}: {bus_fee_per_student} PKR")

    logger.info(f"Phase 3 complete: {total_fees_stored} fee records stored")

    logger.info(f"Overall done: {len(route_to_stop_ids)} routes | {sum(len(v) for v in route_to_stop_ids.values())} stops | {total_fees_stored} fees")

    logger.info(f"Done: {len(route_to_stop_ids)} routes | {sum(len(v) for v in route_to_stop_ids.values())} stops stored")
    
        # Phase 3 complete...
    logger.info(f"Phase 3 complete: {total_fees_stored} fee records stored")

    # ── Final Step: Mark processed students as "successful" registration ─────
    logger.info("Final step: Updating registration_status to 'successful' for all processed students...")

    students_col = get_collection('students')
    success_batch = db.batch()
    success_count = 0

    for bus_id, cluster_stops in bus_clusters.items():
        for stop in cluster_stops:
            student_ids = stop.get('merged_student_ids', [stop.get('student_id', 'unknown')])
            for student_id in student_ids:
                if student_id and student_id != 'unknown':
                    success_batch.update(students_col.document(student_id), {
                        "registration_status": "successful",
                        "updated_at": firestore.SERVER_TIMESTAMP
                    })
                    success_count += 1

                    if success_count % 499 == 0:
                        success_batch.commit()
                        success_batch = db.batch()

    if success_count % 499 != 0:
        success_batch.commit()

    logger.info(f"Updated {success_count} students to 'successful' registration status.")

    # ── Final log ─────────────────────────────────────────────────────────────
    logger.info(f"Overall done: {len(route_to_stop_ids)} routes | {sum(len(v) for v in route_to_stop_ids.values())} stops | {total_fees_stored} fees")