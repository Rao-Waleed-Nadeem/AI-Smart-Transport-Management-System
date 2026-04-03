from sklearn.cluster import KMeans
import numpy as np
from config.settings import BUS_CAPACITY
from config.logging_config import logger
import math

def cluster_stops(merged_stops):
    """
    Use K-Means to group merged stops into bus clusters.
    Number of buses = ceil(total_students / bus_capacity)
    """
    if not merged_stops:
        return {}

    total_students = sum(s['student_count'] for s in merged_stops)
    num_buses = math.ceil(total_students / BUS_CAPACITY)

    if num_buses < 1:
        num_buses = 1

    coords = np.array([[s['lat'], s['lng']] for s in merged_stops])

    # ── Weighted KMeans: repeat each stop's coordinates by its student count ──
    # Plain KMeans treats every stop equally regardless of how many students
    # it serves. Expanding each stop into (student_count) duplicate rows makes
    # the cluster centroid calculation pull toward high-occupancy stops, so
    # each resulting bus cluster carries a roughly equal number of students
    # rather than a roughly equal number of stops.
    weighted_coords = np.repeat(coords, [s['student_count'] for s in merged_stops], axis=0)

    kmeans = KMeans(n_clusters=num_buses, random_state=42, n_init=10)
    kmeans.fit(weighted_coords)  # fit on weighted data

    # Assign each real stop to its nearest cluster centre
    centers = kmeans.cluster_centers_
    labels = np.array([
        int(np.argmin([np.linalg.norm(coord - c) for c in centers]))
        for coord in coords
    ])

    clusters = {i: [] for i in range(num_buses)}
    for idx, label in enumerate(labels):
        clusters[label].append(merged_stops[idx])

    logger.info(f"Clustered into {num_buses} buses (capacity {BUS_CAPACITY}):")
    for bus_id, stops in clusters.items():
        student_count = sum(s['student_count'] for s in stops)
        logger.info(f"  Bus {bus_id}: {len(stops)} stops, {student_count} students")

    return clusters