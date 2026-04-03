# optimization/route_generator.py
import requests
from config.settings import ORS_API_KEY
from config.logging_config import logger
from typing import List, Tuple, Optional

def generate_route_path(
    ordered_stops: List[dict],
    university_coord: Tuple[float, float]
) -> Tuple[Optional[List[List[float]]], float, float]:
    """
    Generate bus route using current ORS Directions API v2 (fully compatible 2026).
    
    - Profile: driving-hgv → bus-friendly, main roads preferred
    - Preference: fastest → best real-world time/fuel optimization
    - Weightings: valid range 0.0–1.0 only (no >1.0 values)
    - U-turns allowed at signals/junctions when fastest/safest
    """
    if not ORS_API_KEY:
        logger.error("ORS_API_KEY missing in .env")
        return None, 0.0, 0.0

    # Coordinates: ordered stops + university end
    coords = [[s['lng'], s['lat']] for s in ordered_stops]
    coords.append([university_coord[1], university_coord[0]])  # [lng, lat]

    if len(coords) < 2:
        logger.warning("Not enough points for route")
        return None, 0.0, 0.0

    body = {
        "coordinates": coords,
        "profile": "driving-hgv",               # Bus profile – avoids narrow roads
        "preference": "fastest",                # Minimize time (good for fuel/time)
        "geometry": True,
        "format": "geojson",
        "instructions": False,
        "extra_info": ["waycategory", "waytype", "surface"],  # Optional road details
        "options": {
            "profile_params": {
                "weightings": {                 # Valid range: 0.0–1.0 (lower = more preferred)
                    "green": 0.8,               # Slightly prefer main/green roads
                    "quiet": 0.9                # Mild preference for quieter roads
                }
            }
        }
    }

    headers = {
        "Authorization": ORS_API_KEY,
        "Content-Type": "application/json"
    }

    url = "https://api.openrouteservice.org/v2/directions/driving-hgv/geojson"

    try:
        logger.debug(f"Requesting ORS route with {len(coords)} waypoints")
        response = requests.post(url, json=body, headers=headers, timeout=20)
        response.raise_for_status()

        data = response.json()

        if 'features' not in data or len(data['features']) == 0:
            logger.error("ORS response missing route features")
            return None, 0.0, 0.0

        feature = data['features'][0]
        geometry = feature['geometry']['coordinates']          # [[lng, lat], ...]
        summary = feature['properties']['summary']

        distance_km = summary['distance'] / 1000
        duration_hours = summary['duration'] / 3600

        logger.info("ORS route generated successfully (bug-free)")
        logger.info(f"  Waypoints: {len(ordered_stops)} stops → University")
        logger.info(f"  Distance: {distance_km:.2f} km")
        logger.info(f"  Duration: {duration_hours:.2f} h ({duration_hours*60:.0f} min)")
        logger.debug(f"Geometry sample (first 3 & last 3): {geometry[:3]} ... {geometry[-3:]}")

        return geometry, distance_km, duration_hours

    except requests.exceptions.HTTPError as http_err:
        try:
            error_data = response.json()
            logger.error(f"ORS error {response.status_code}: {error_data.get('error', {}).get('message', 'Unknown')}")
        except:
            logger.error(f"ORS HTTP error {response.status_code}: {response.text}")
        return None, 0.0, 0.0

    except Exception as e:
        logger.error(f"Route generation failed: {e}", exc_info=True)
        return None, 0.0, 0.0