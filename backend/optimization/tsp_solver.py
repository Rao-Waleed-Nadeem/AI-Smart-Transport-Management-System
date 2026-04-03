# optimization/tsp_solver.py
from ortools.constraint_solver import routing_enums_pb2
from ortools.constraint_solver import pywrapcp
import requests
from utils.geo_utils import haversine_distance
from config.settings import ORS_API_KEY, UNIVERSITY_COORD
from config.logging_config import logger

def get_road_distance_matrix(points: list[dict]):
    """
    Use ORS to compute real road distance matrix (meters) between all points.
    Profile: 'driving-hgv' for bus-like routing (avoids narrow/unaccessible roads).
    Returns: 2D list [i][j] = distance from point i to j.
    
    Real-world: Considers actual roads, speed limits, turns → better for time/fuel.
    """
    if not ORS_API_KEY:
        logger.warning("No ORS_API_KEY → fallback to haversine")
        return None

    coords = [[p['lng'], p['lat']] for p in points]  # ORS expects [lng, lat]

    body = {
        "locations": coords,
        "metrics": ["distance"],
        "units": "m",
        "profile": "driving-hgv"  # Bus/heavy vehicle — optimizes for accessible roads
    }

    headers = {"Authorization": ORS_API_KEY}
    url = "https://api.openrouteservice.org/v2/matrix/driving-hgv"

    try:
        response = requests.post(url, json=body, headers=headers)
        response.raise_for_status()
        data = response.json()
        matrix = data['distances']  # 2D list: matrix[i][j] = distance i→j in meters
        logger.info(f"ORS matrix computed: {len(matrix)}x{len(matrix)}")
        return matrix
    except Exception as e:
        logger.error(f"ORS matrix error: {e} → fallback to haversine")
        return None

def compute_haversine_matrix(points: list[dict]):
    """
    Fallback: Straight-line distance matrix (meters).
    Less accurate for roads/time/fuel, but fast.
    """
    n = len(points)
    matrix = [[0] * n for _ in range(n)]
    for i in range(n):
        for j in range(n):
            if i != j:
                matrix[i][j] = int(haversine_distance(
                    (points[i]['lat'], points[i]['lng']),
                    (points[j]['lat'], points[j]['lng'])
                ))
    logger.info("Fallback haversine matrix computed")
    return matrix

def solve_tsp(stops: list[dict], university_coord: tuple[float, float] = UNIVERSITY_COORD, fixed_end: bool = True):
    """
    Solve open TSP: optimal order to visit all stops, end at university (or anywhere if fixed_end=False).
    Uses ORS road matrix for best real-road optimization (time/fuel/bus roads).
    """
    if not stops:
        logger.warning("No stops for TSP")
        return []

    # Prepare points
    if fixed_end:
        # University as last point
        all_points = stops + [{'lat': university_coord[0], 'lng': university_coord[1], 'student_id': 'university', 'student_count': 0}]
        end_index = len(all_points) - 1
    else:
        # University as start (for evening drop), end anywhere
        all_points = [{'lat': university_coord[0], 'lng': university_coord[1], 'student_id': 'university', 'student_count': 0}] + stops
        end_index = -1  # no fixed end

    num_points = len(all_points)

    # Get real road distance matrix (ORS preferred)
    matrix = get_road_distance_matrix(all_points)
    if matrix is None:
        matrix = compute_haversine_matrix(all_points)

    # ─── OR-Tools Setup ───
    manager = pywrapcp.RoutingIndexManager(num_points, 1, 0)  # 1 vehicle, start at index 0
    routing = pywrapcp.RoutingModel(manager)

    def distance_callback(from_index, to_index):
        from_node = manager.IndexToNode(int(from_index))   # safe cast
        to_node   = manager.IndexToNode(int(to_index))
        return int(matrix[from_node][to_node])

    transit_index = routing.RegisterTransitCallback(distance_callback)
    routing.SetArcCostEvaluatorOfAllVehicles(transit_index)

    # Force end at university if fixed_end=True
    if fixed_end:
        routing.AddDisjunction([end_index], 0)  # university must be visited, but no extra cost

    # Search parameters
    search_parameters = pywrapcp.DefaultRoutingSearchParameters()
    search_parameters.first_solution_strategy = routing_enums_pb2.FirstSolutionStrategy.PATH_CHEAPEST_ARC
    search_parameters.local_search_metaheuristic = routing_enums_pb2.LocalSearchMetaheuristic.GUIDED_LOCAL_SEARCH
    search_parameters.time_limit.seconds = 15  # give more time for better solution

    solution = routing.SolveWithParameters(search_parameters)

    if solution:
        ordered_indices = []
        index = routing.Start(0)
        while not routing.IsEnd(index):
            node = manager.IndexToNode(int(index))
            ordered_indices.append(node)
            index = solution.Value(routing.NextVar(index))

        # Extract ordered stops (exclude university if fixed_end)
        ordered_stops = [all_points[i] for i in ordered_indices if all_points[i]['student_id'] != 'university']

        total_distance = solution.ObjectiveValue()
        logger.info(f"TSP solved: {len(ordered_stops)} stops, distance {total_distance} m, objective {solution.ObjectiveValue()}")
        logger.debug(f"Order: {[s.get('stop_name') or s.get('stop_id', 'unknown') for s in ordered_stops]}")

        return ordered_stops

    logger.error("TSP solver failed — returning original order")
    return stops
    """
    Solve TSP for one bus's stops: find optimal order minimizing road distance/time/fuel.
    University = fixed last stop.
    
    Uses ORS road matrix for best optimization (bus roads, turns, etc.).
    Fallback: haversine if API fails.
    
    Returns: ordered list of stops (dicts) in visit order.
    """
    if not stops:
        logger.warning("No stops for TSP")
        return []

    # Add university as last stop (index = len(stops))
    all_points = stops + [{'lat': university_coord[0], 'lng': university_coord[1], 'student_count': 0, 'student_id': 'university'}]
    num_points = len(all_points)

    # Get distance matrix (prefer ORS road-based)
    matrix = get_road_distance_matrix(all_points)
    if matrix is None:
        matrix = compute_haversine_matrix(all_points)

    # Setup OR-Tools
    manager = pywrapcp.RoutingIndexManager(num_points, 1, num_points - 1)  # 1 vehicle, start anywhere, end at university (last index)
    routing = pywrapcp.RoutingModel(manager)

    def distance_callback(from_index, to_index):
        from_node = manager.IndexToNode(int(from_index))   # ← force int
        to_node   = manager.IndexToNode(int(to_index))     # ← force int
        return int(haversine_distance(
        (all_points[from_node]['lat'], all_points[from_node]['lng']),
        (all_points[to_node]['lat'], all_points[to_node]['lng'])
    ))

    transit_callback_index = routing.RegisterTransitCallback(distance_callback)
    routing.SetArcCostEvaluatorOfAllVehicles(transit_callback_index)

    # Search parameters (better heuristic for road-optimized paths)
    search_parameters = pywrapcp.DefaultRoutingSearchParameters()
    search_parameters.first_solution_strategy = routing_enums_pb2.FirstSolutionStrategy.PATH_CHEAPEST_ARC
    search_parameters.local_search_metaheuristic = routing_enums_pb2.LocalSearchMetaheuristic.GUIDED_LOCAL_SEARCH
    search_parameters.time_limit.seconds = 10  # Limit to 10s for larger clusters

    # Solve
    solution = routing.SolveWithParameters(search_parameters)

    if solution:
        # Extract order
        ordered_indices = []
        index = routing.Start(0)
        while not routing.IsEnd(index):
            ordered_indices.append(manager.IndexToNode(index))
            index = solution.Value(routing.NextVar(index))
        
        # Get stops in order (exclude university)
        ordered_stops = [all_points[i] for i in ordered_indices[:-1]]
        
        logger.info(f"TSP solved for {len(stops)} stops: objective {solution.ObjectiveValue()} m")
        logger.debug(f"Ordered stops: {[s['student_id'] for s in ordered_stops]}")
        return ordered_stops
    
    logger.error("TSP no solution found — check matrix or points")
    return stops  # Fallback to original order