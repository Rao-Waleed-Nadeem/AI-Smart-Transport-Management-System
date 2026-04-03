import math
from config.logging_config import logger

def haversine_distance(coord1: tuple[float, float], coord2: tuple[float, float]) -> float:
    """
    Calculate distance between two (lat, lng) points in meters
    Real-world: how far is house A from house B on Earth's surface
    """
    R = 6371000  # Earth radius in meters
    lat1, lon1 = math.radians(coord1[0]), math.radians(coord1[1])
    lat2, lon2 = math.radians(coord2[0]), math.radians(coord2[1])
    
    dlat = lat2 - lat1
    dlon = lon2 - lon1
    
    a = math.sin(dlat / 2)**2 + math.cos(lat1) * math.cos(lat2) * math.sin(dlon / 2)**2
    c = 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))
    
    distance_m = R * c
    logger.debug(f"Haversine: {coord1} → {coord2} = {distance_m:.1f} m")
    return distance_m