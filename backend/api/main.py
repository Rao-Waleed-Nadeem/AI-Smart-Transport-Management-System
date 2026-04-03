# api/main.py
from fastapi import FastAPI, HTTPException
from data_pipeline.student_loader import load_student_stops
from data_pipeline.stop_builder import snap_stops_to_roads, merge_nearby_stops, update_student_stops
from osm.fetch_roads import fetch_or_load_road_graph
from osm.road_filter import filter_main_roads
from clustering.stop_clustering import cluster_stops
from optimization.store_routes import store_optimized_routes
from config.settings import UNIVERSITY_COORD
from config.logging_config import logger

app = FastAPI(
    title="AI Smart Transport Optimization API",
    description="Backend for university bus routing, clustering, TSP, and fee calculation",
    version="1.0.0"
)

def run_optimization():
    """
    Full end-to-end pipeline:
    1. Load student stops
    2. Load & filter main roads
    3. Snap stops to nearest main road
    4. Merge nearby stops
    5. Update students with final coordinates
    6. Cluster stops into buses
    7. TSP ordering + real route generation + store routes/stops/fees
    """
    logger.info("Starting full route optimization pipeline")

    # Step 6: Load raw student data
    stops = load_student_stops()
    if not stops:
        raise ValueError("No student stops found in Firestore")

    # Steps 7–8: Road graph
    graph = fetch_or_load_road_graph()
    if graph is None:
        raise RuntimeError("Failed to load road graph")

    main_graph = filter_main_roads(graph)
    if main_graph is None or len(main_graph.edges) == 0:
        raise RuntimeError("No main roads available after filtering")

    # Step 9: Snap
    snapped = snap_stops_to_roads(stops, main_graph)
    if not snapped:
        raise ValueError("No stops snapped successfully")

    # Step 11: Merge
    merged = merge_nearby_stops(snapped)

    # Step 13: Update students in Firestore
    # update_student_stops(merged)

    # Step 14: Cluster into buses
    clusters = cluster_stops(merged)
    if not clusters:
        raise ValueError("Clustering produced no bus groups")

    # Steps 15–17: TSP + route path + store everything
    store_optimized_routes(clusters)

    logger.info(f"Pipeline completed successfully | {len(clusters)} buses processed")
    return {
        "status": "success",
        "message": "Routes optimized, fees calculated, and stored in Firestore",
        "bus_count": len(clusters),
        "total_students_processed": sum(sum(s['student_count'] for s in cluster) for cluster in clusters.values())
    }

@app.post("/optimize-routes")
def optimize_routes():
    """
    Public endpoint: Admin calls this to trigger full route optimization.
    Returns success/failure for Kotlin app to show toast/notification.
    """
    try:
        result = run_optimization()
        return result
    except ValueError as ve:
        logger.error(f"Validation error: {ve}")
        raise HTTPException(status_code=400, detail=str(ve))
    except RuntimeError as re:
        logger.error(f"Runtime error: {re}")
        raise HTTPException(status_code=500, detail=str(re))
    except Exception as e:
        logger.critical(f"Unexpected pipeline error: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail=f"Internal server error: {str(e)}")