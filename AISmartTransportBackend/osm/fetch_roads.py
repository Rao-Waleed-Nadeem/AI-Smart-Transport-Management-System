# osm/fetch_roads.py
import osmnx as ox
import os
from config.settings import FAISALABAD_BOUNDS
from config.logging_config import logger

CACHE_PATH = "cache/road_graph_fsd.graphml"
os.makedirs("cache", exist_ok=True)

def fetch_or_load_road_graph(force_redownload: bool = False):
    """
    Downloads drive network for Faisalabad bounding box using OSMnx,
    or loads from cache if exists.
    
    Uses 'drive' network_type → car/bus accessible roads.
    """
    if not force_redownload and os.path.exists(CACHE_PATH):
        try:
            logger.info("Loading cached Faisalabad road graph...")
            graph = ox.load_graphml(CACHE_PATH)
            logger.info(f"Loaded graph with {len(graph.nodes)} nodes and {len(graph.edges)} edges")
            return graph
        except Exception as e:
            logger.warning(f"Failed to load cached graph: {e} → will re-download")

    # Define bounding box from settings
   # osm/fetch_roads.py  (replace the try block content)

    # Define bounding box as tuple: (left/west, bottom/south, right/east, top/north)
    bbox = (
        FAISALABAD_BOUNDS["sw"][1],   # west   = min lng
        FAISALABAD_BOUNDS["sw"][0],   # south  = min lat
        FAISALABAD_BOUNDS["ne"][1],   # east   = max lng
        FAISALABAD_BOUNDS["ne"][0]    # north  = max lat
    )

    logger.info(f"Downloading road network for Faisalabad bbox: {bbox}")

    try:
        # network_type='drive' → main roads suitable for buses
        graph = ox.graph_from_bbox(
            bbox=bbox,                      # ← single tuple argument
            network_type="drive",
            simplify=True,
            retain_all=False,               # remove disconnected subgraphs
            truncate_by_edge=True
        )

        # Save to cache
        ox.save_graphml(graph, CACHE_PATH)
        logger.info(f"Downloaded and cached graph → {len(graph.nodes)} nodes, {len(graph.edges)} edges")
        logger.info(f"Graph saved to: {os.path.abspath(CACHE_PATH)}")

        return graph

    except Exception as e:
        logger.error(f"Failed to download road graph: {e}", exc_info=True)
        return None