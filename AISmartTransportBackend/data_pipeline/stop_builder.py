# data_pipeline/stop_builder.py
from utils.distance_utils import find_nearest_road_point  # we'll create in Step 10
from utils.geo_utils import haversine_distance
from config.settings import MAX_SNAP_DISTANCE_METERS, MERGE_DISTANCE_METERS
from config.logging_config import logger
from typing import List, Dict, Any

def snap_stops_to_roads(
    stops: List[Dict[str, Any]],
    main_graph
) -> List[Dict[str, Any]]:
    """
    For each student stop, find the nearest point on a main road
    and snap it there — but only if within MAX_SNAP_DISTANCE_METERS.
    """
    if main_graph is None:
        logger.error("No main road graph provided for snapping")
        return stops  # return original as fallback

    snapped_stops = []
    skipped = 0

    for stop in stops:
        original_coord = (stop['lat'], stop['lng'])
        
        try:
            nearest_coord = find_nearest_road_point(main_graph, original_coord)
            distance_m = haversine_distance(original_coord, nearest_coord)
            
            if distance_m <= MAX_SNAP_DISTANCE_METERS:
                snapped_stop = stop.copy()
                snapped_stop['original_lat'] = stop['lat']   # keep original for reference
                snapped_stop['original_lng'] = stop['lng']
                snapped_stop['lat'] = nearest_coord[0]
                snapped_stop['lng'] = nearest_coord[1]
                snapped_stop['snap_distance_m'] = round(distance_m, 2)
                snapped_stops.append(snapped_stop)
                logger.debug(f"Snapped {stop['name']} ({stop['roll_no']}): {distance_m:.1f}m")
            else:
                logger.warning(f"Stop {stop['name']} too far from main road ({distance_m:.1f}m) → skipped")
                skipped += 1
                
        except Exception as e:
            logger.error(f"Snapping failed for student {stop['student_id']}: {e}")
            skipped += 1

    logger.info(f"Snapping complete:")
    logger.info(f"  Input stops: {len(stops)}")
    logger.info(f"  Snapped successfully: {len(snapped_stops)}")
    logger.info(f"  Skipped (too far): {skipped}")

    if snapped_stops:
        logger.debug(f"First snapped stop example: {snapped_stops[0]}")

    return snapped_stops

def merge_nearby_stops(snapped_stops):
    """
    After snapping: if multiple students are very close (< MERGE_DISTANCE_METERS),
    combine them into one stop and increase student_count.
    
    Real-world: if 4 students live within 150–200 m after snapping → one shared bus stop
    """
    if not snapped_stops:
        return []

    merged = []
    MERGE_DISTANCE = MERGE_DISTANCE_METERS  # from settings (200 m default)

    for stop in snapped_stops:
        found = False
        for m in merged:
            dist = haversine_distance(
                (stop['lat'], stop['lng']),
                (m['lat'], m['lng'])
            )
            if dist < MERGE_DISTANCE:
                m['student_count'] += stop['student_count']
                # Optional: keep list of original student_ids for traceability
                if 'merged_student_ids' not in m:
                    m['merged_student_ids'] = [m['student_id']]
                m['merged_student_ids'].append(stop['student_id'])
                found = True
                break
        
        if not found:
            new_stop = stop.copy()
            new_stop['merged_student_ids'] = [stop['student_id']]  # even single stop
            merged.append(new_stop)

    logger.info(f"Merged stops:")
    logger.info(f"  Before merge: {len(snapped_stops)} stops")
    logger.info(f"  After merge : {len(merged)} stops (merged {len(snapped_stops) - len(merged)} pairs/groups)")
    if merged:
        logger.debug(f"First few merged stops: {merged[:3]}")

    return merged

from database.firebase import get_collection, update_document

def update_student_stops(merged_stops):
    """
    After merging: update each student's stop_lat / stop_lng in Firestore
    to the final merged stop location.
    
    Note: If multiple students merged to same stop → they all get same coordinates.
    """
    students_col = get_collection('students')
    updated_count = 0

    for merged_stop in merged_stops:
        final_lat = merged_stop['lat']
        final_lng = merged_stop['lng']
        
        # Update every student that belongs to this merged stop
        for student_id in merged_stop.get('merged_student_ids', [merged_stop['student_id']]):
            try:
                update_document('students', student_id, {
                    'latitude': final_lat,
                    'longitude': final_lng,
                    # 'snap_distance_m': merged_stop.get('snap_distance_m', 0),
                    # 'is_merged': len(merged_stop.get('merged_student_ids', [])) > 1
                })
                updated_count += 1
            except Exception as e:
                logger.error(f"Failed to update student {student_id}: {e}")

    logger.info(f"Updated {updated_count} students in Firestore with final snapped/merged coordinates")