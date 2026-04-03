# config/settings.py
import os
from dotenv import load_dotenv

load_dotenv()  # This loads variables from .env file

# API Keys
ORS_API_KEY = os.getenv("ORS_API_KEY")
# GOOGLE_API_KEY = os.getenv("GOOGLE_API_KEY")  # optional

# Firebase
FIREBASE_CREDENTIALS_PATH = os.getenv("FIREBASE_CREDENTIALS_PATH")

# Constants
UNIVERSITY_COORD = (
    float(os.getenv("UNIVERSITY_LAT", 31.60253)),
    float(os.getenv("UNIVERSITY_LNG", 73.03485))
)
BUS_CAPACITY = int(os.getenv("BUS_CAPACITY", 30))
MAX_SNAP_DISTANCE_METERS = float(os.getenv("MAX_SNAP_DISTANCE_METERS", 100))
MERGE_DISTANCE_METERS = float(os.getenv("MERGE_DISTANCE_METERS", 200))

# Faisalabad bounds
FAISALABAD_BOUNDS = {
    "sw": (
        float(os.getenv("FAISALABAD_BOUNDS_SW_LAT", 31.30)),
        float(os.getenv("FAISALABAD_BOUNDS_SW_LNG", 73.00))
    ),
    "ne": (
        float(os.getenv("FAISALABAD_BOUNDS_NE_LAT", 31.55)),
        float(os.getenv("FAISALABAD_BOUNDS_NE_LNG", 73.20))
    )
}

# Quick validation
if not ORS_API_KEY:
    raise ValueError("ORS_API_KEY is missing in .env file")

FUEL_PRICE_PER_LITER = float(os.getenv("FUEL_PRICE_PER_LITER", 250))
BUS_FUEL_EFFICIENCY_KM_PER_LITER = float(os.getenv("BUS_FUEL_EFFICIENCY_KM_PER_LITER", 5))
PROFIT_MARGIN_PERCENT = float(os.getenv("PROFIT_MARGIN_PERCENT", 20))
SEMESTER_MONTHS = int(os.getenv("SEMESTER_MONTHS", 4))
DAYS_PER_MONTH = int(os.getenv("DAYS_PER_MONTH", 20))