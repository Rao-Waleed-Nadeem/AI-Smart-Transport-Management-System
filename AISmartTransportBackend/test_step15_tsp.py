# test_step15_tsp_numbered.py
from data_pipeline.student_loader import load_student_stops
from data_pipeline.stop_builder import snap_stops_to_roads, merge_nearby_stops
from osm.fetch_roads import fetch_or_load_road_graph
from osm.road_filter import filter_main_roads
from clustering.stop_clustering import cluster_stops
from optimization.tsp_solver import solve_tsp
import folium
from config.settings import UNIVERSITY_COORD

# ─── Load & process (same as before) ───
stops = load_student_stops()[:50]
graph = fetch_or_load_road_graph()
main_graph = filter_main_roads(graph)
snapped = snap_stops_to_roads(stops, main_graph)
merged = merge_nearby_stops(snapped)
clusters = cluster_stops(merged)

# ─── Create map ───
m = folium.Map(location=[UNIVERSITY_COORD[0], UNIVERSITY_COORD[1]], zoom_start=12, tiles="cartodbpositron")

# Colors for each bus route line
colors = ['red', 'blue', 'green', 'purple', 'orange', 'darkred', 'cadetblue', 'darkblue', 'pink']

for bus_id, cluster_stops in clusters.items():
    color = colors[bus_id % len(colors)]
    
    # Solve TSP → get ordered stops
    ordered_stops = solve_tsp(cluster_stops)
    
    if not ordered_stops:
        print(f"Bus {bus_id}: TSP failed, skipping")
        continue
    
    # Build coordinates for the route line (stops + university)
    route_coords = [[s['lat'], s['lng']] for s in ordered_stops] + [UNIVERSITY_COORD]
    
    # Draw the optimized route line
    folium.PolyLine(
        locations=route_coords,
        color=color,
        weight=4,
        opacity=0.9,
        tooltip=f"Bus {bus_id} - {len(ordered_stops)} stops → University",
        popup=f"Bus {bus_id} route (optimized TSP order)"
    ).add_to(m)
    
    # Numbered markers: 1, 2, 3, ... for order
    for idx, stop in enumerate(ordered_stops, start=1):
        popup_text = (
            f"<b>Bus {bus_id} - Stop {idx}</b><br>"
            f"Students: {stop['student_count']}<br>"
            f"Original name: {stop.get('name', 'Merged group')}<br>"
            f"ID: {stop['student_id']}"
        )
        
        # Use DivIcon with number inside circle
        folium.Marker(
            location=[stop['lat'], stop['lng']],
            popup=popup_text,
            icon=folium.DivIcon(
                html=f"""
                <div style="
                    background-color: {color};
                    color: white;
                    width: 28px;
                    height: 28px;
                    border-radius: 50%;
                    text-align: center;
                    line-height: 28px;
                    font-weight: bold;
                    font-size: 14px;
                    border: 2px solid white;
                    box-shadow: 2px 2px 4px rgba(0,0,0,0.4);
                ">
                    {idx}
                </div>
                """
            ),
            tooltip=f"Stop {idx} - Bus {bus_id}"
        ).add_to(m)

# Final university marker
folium.Marker(
    UNIVERSITY_COORD,
    popup="University (final destination for all buses)",
    icon=folium.Icon(color="black", icon="school", prefix="fa")
).add_to(m)

# Save & inform
output_file = "step15_tsp_routes.html"
m.save(output_file)
print(f"Map saved: {output_file}")
print("→ Open in browser: numbered circles 1,2,3... show exact visit order per bus")
print(f"→ Colored lines = route path for each bus")
print(f"Total buses shown: {len(clusters)}")