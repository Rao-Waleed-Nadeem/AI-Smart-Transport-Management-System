# osm/road_filter.py
import osmnx as ox
from config.logging_config import logger
from typing import Optional

# These are the OSM highway types we consider "main roads" suitable for buses
# (avoid residential, service, footway, cycleway, narrow lanes, etc.)
MAIN_HIGHWAY_TYPES = [
    'primary',
    'primary_link',
    'secondary',
    'secondary_link',
    'tertiary',
    'tertiary_link',
    'trunk',
    'trunk_link',
    # 'motorway',           # usually too far/fast — optional, can include if needed
    # 'motorway_link',
]

def filter_main_roads(graph):
    if graph is None:
        logger.error("No graph provided to filter")
        return None

    try:
        main_edges = [
            (u, v, k) for u, v, k, d in graph.edges(keys=True, data=True)
            if d.get('highway') in MAIN_HIGHWAY_TYPES
        ]

        if not main_edges:
            logger.warning("No main road edges found after filtering!")
            return None

        main_graph = graph.edge_subgraph(main_edges)

        logger.info(f"Filtered graph:")
        logger.info(f"  Original: {len(graph.nodes)} nodes, {len(graph.edges)} edges")
        logger.info(f"  After filter (main roads only): {len(main_graph.nodes)} nodes, {len(main_graph.edges)} edges")

        if len(main_graph.edges) == 0:
            logger.warning("Filtered graph is empty — no usable main roads")
            return None

        return main_graph

    except Exception as e:
        logger.error(f"Error during road filtering: {e}", exc_info=True)
        return None
    """
    Filters the OSMnx graph to keep only main/bus-accessible roads.
    
    Returns a subgraph containing only edges where highway is in MAIN_HIGHWAY_TYPES.
    """
    if graph is None:
        logger.error("No graph provided to filter")
        return None

    try:
        # Collect edges that match main highway types
        main_edges = []
        for u, v, key, data in graph.edges(keys=True, data=True):
            highway = data.get('highway')
            if highway in MAIN_HIGHWAY_TYPES:
                main_edges.append((u, v, key))

        if not main_edges:
            logger.warning("No main road edges found after filtering!")
            return None

        # Create subgraph with only main edges
        main_graph = graph.edge_subgraph(main_edges)

        logger.info(f"Filtered graph:")
        logger.info(f"  Original: {len(graph.nodes)} nodes, {len(graph.edges)} edges")
        logger.info(f"  After filter (main roads only): {len(main_graph.nodes)} nodes, {len(main_graph.edges)} edges")
        logger.debug(f"Example edge data: {graph.edges[next(iter(main_edges))][0]}")

        return main_graph

    except Exception as e:
        logger.error(f"Error filtering main roads: {e}", exc_info=True)
        return None