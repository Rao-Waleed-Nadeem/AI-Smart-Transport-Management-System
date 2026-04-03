from utils.geo_utils import haversine_distance

print(haversine_distance((31.389340, 73.148533), (31.389512, 73.148720)))   # should be very small ~20–30 m
print(haversine_distance((31.40, 73.10), (31.50, 73.20)))                   # larger distance ~15–20 km