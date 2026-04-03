# utils/distance_utils.py
import numpy as np
from sklearn.neighbors import BallTree
import networkx as nx
from config.logging_config import logger
from utils.geo_utils import haversine_distance  # we'll create next if not already

def build_node_balltree(graph):
    """
    Builds a BallTree from all graph nodes for fast nearest-neighbor search.
    Returns: BallTree, list of node IDs in same order
    """
    # Collect node coordinates (lat, lng)
    node_coords = []
    node_ids = []
    
    for node, data in graph.nodes(data=True):
        if 'y' in data and 'x' in data:  # OSMnx uses 'y'=lat, 'x'=lng
            node_coords.append([data['y'], data['x']])
            node_ids.append(node)
    
    if not node_coords:
        raise ValueError("Graph has no nodes with coordinates")
    
    # BallTree expects radians for haversine
    coords_rad = np.deg2rad(node_coords)
    tree = BallTree(coords_rad, metric='haversine')
    
    logger.debug(f"Built BallTree with {len(node_ids)} nodes")
    return tree, node_ids


def find_nearest_road_point(graph, query_coord: tuple[float, float]) -> tuple[float, float]:
    """
    Finds the nearest node in the graph to the query point (lat, lng).
    Returns (lat, lng) of the nearest graph node.
    
    For better accuracy later we can project to nearest edge — but node is good start.
    """
    tree, node_ids = build_node_balltree(graph)
    
    # Query in radians
    query_rad = np.deg2rad([[query_coord[0], query_coord[1]]])
    
    # Find nearest node index
    dist_rad, idx = tree.query(query_rad, k=1)
    nearest_node_id = node_ids[idx[0][0]]
    
    # Get coordinates
    nearest_node = graph.nodes[nearest_node_id]
    nearest_lat = nearest_node['y']
    nearest_lng = nearest_node['x']
    
    distance_m = haversine_distance(query_coord, (nearest_lat, nearest_lng))
    
    logger.debug(f"Nearest road node to {query_coord}: ({nearest_lat:.6f}, {nearest_lng:.6f}) — {distance_m:.1f}m")
    
    return (nearest_lat, nearest_lng)