# test_step16_route_geometry_all_buses.py
from data_pipeline.student_loader import load_student_stops
from data_pipeline.stop_builder import snap_stops_to_roads, merge_nearby_stops
from osm.fetch_roads import fetch_or_load_road_graph
from osm.road_filter import filter_main_roads
from clustering.stop_clustering import cluster_stops
from optimization.tsp_solver import solve_tsp
from optimization.route_generator import generate_route_path
import folium
from config.settings import UNIVERSITY_COORD

# Load & process data
stops = load_student_stops()[:50]
graph = fetch_or_load_road_graph()
main_graph = filter_main_roads(graph)
snapped = snap_stops_to_roads(stops, main_graph)
merged = merge_nearby_stops(snapped)
clusters = cluster_stops(merged)

# Create map
m = folium.Map(location=[UNIVERSITY_COORD[0], UNIVERSITY_COORD[1]], zoom_start=11, tiles="cartodbpositron")

# Colors for each bus route
colors = ['red', 'blue', 'green', 'purple', 'orange', 'darkred', 'cadetblue', 'pink', 'brown']

for bus_id, cluster_stops in clusters.items():
    color = colors[bus_id % len(colors)]
    
    print(f"Generating route for Bus {bus_id} ({len(cluster_stops)} stops)")
    
    ordered_stops = solve_tsp(cluster_stops)
    
    if not ordered_stops:
        print(f"  TSP failed for Bus {bus_id}")
        continue
    
    geometry, distance_km, duration_h = generate_route_path(ordered_stops, UNIVERSITY_COORD)
    
    if geometry:
        # Draw the real ORS route polyline
        folium.PolyLine(
            locations=[[lat, lng] for lng, lat in geometry],  # ORS returns [lng, lat]
            color=color,
            weight=5,
            opacity=0.9,
            tooltip=f"Bus {bus_id} - {distance_km:.1f} km, {duration_h*60:.0f} min",
            popup=f"Bus {bus_id} route (optimized)"
        ).add_to(m)
        
        # Numbered markers for stops in order
        for idx, stop in enumerate(ordered_stops, start=1):
            popup_text = (
                f"<b>Bus {bus_id} - Stop {idx}</b><br>"
                f"Students: {stop['student_count']}<br>"
                f"Name/ID: {stop.get('name', 'Group')} ({stop['student_id']})"
            )
            
            folium.Marker(
                [stop['lat'], stop['lng']],
                popup=popup_text,
                icon=folium.DivIcon(
                    html=f"""
                    <div style="
                        background-color: {color};
                        color: white;
                        width: 30px;
                        height: 30px;
                        border-radius: 50%;
                        text-align: center;
                        line-height: 30px;
                        font-weight: bold;
                        font-size: 14px;
                        border: 2px solid white;
                        box-shadow: 2px 2px 5px rgba(0,0,0,0.5);
                    ">{idx}</div>
                    """
                ),
                tooltip=f"Bus {bus_id} - Stop {idx}"
            ).add_to(m)
    else:
        print(f"  Route generation failed for Bus {bus_id}")

# University marker (end point for all buses)
folium.Marker(
    UNIVERSITY_COORD,
    popup="University (final drop-off)",
    icon=folium.Icon(color="black", icon="school", prefix="fa")
).add_to(m)

# Save map
output_file = "step16_all_buses_routes.html"
m.save(output_file)
print(f"\nMap saved: {output_file}")
print("→ Open in browser: each bus has colored route + numbered stops (1,2,3...)")
print(f"Total buses shown: {len(clusters)}")