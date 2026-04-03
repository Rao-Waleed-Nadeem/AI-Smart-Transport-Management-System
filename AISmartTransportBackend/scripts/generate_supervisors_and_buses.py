# scripts/generate_supervisors_and_buses.py
import sys
import os
import random
import string
import time

# Add project root to sys.path so we can import modules from the root
sys.path.append(os.path.abspath(os.path.join(os.path.dirname(__file__), '..')))

from firebase_admin import auth, firestore
from database.firebase import get_collection
from config.logging_config import logger

# Collections
users_col     = get_collection('users')
buses_col     = get_collection('buses')

def random_password(length=12):
    """Generate a secure random password for supervisors (shown in log for testing)"""
    chars = string.ascii_letters + string.digits + "!@#$%^&*"
    return ''.join(random.choice(chars) for _ in range(length))

def random_plate_number():
    """Generate Pakistani-style plate: ABC-1234 or similar"""
    letters = ''.join(random.choices(string.ascii_uppercase, k=3))
    numbers = ''.join(random.choices(string.digits, k=4))
    return f"{letters}-{numbers}"

def random_bus_number():
    """Simple bus number: B-01, B-02, ..."""
    return f"B-{random.randint(1, 99):02d}"

def create_supervisor(idx: int):
    email = f"supervisor{idx}@nu.edu.pk"
    password = "waleed"
    name = f"Supervisor {idx}"

    try:
        # 1. Create Firebase Auth user
        user = auth.create_user(
            email=email,
            password=password,
            display_name=name,
            email_verified=False
        )
        uid = user.uid

        logger.info(f"Created supervisor auth → UID: {uid} | {email} | pass: {password}")
        
         # Step 2: Create student document in 'students' with doc ID = STUDENT-XXX
        doc_id = f"SUPERVISOR-{idx:03d}"  # SUPERVISOR-001, SUPERVISOR-002, ...

        # 2. Create user document with role = supervisor
        user_doc = {
            'email': email,
            'name': name,
            'role': 'supervisor',
            'contact': f"03{random.randint(0,9)}{random.randint(10000000,99999999)}",
            'created_at': firestore.SERVER_TIMESTAMP,
            'updated_at': firestore.SERVER_TIMESTAMP
        }
        users_col.document(uid).set(user_doc)
        logger.debug(f"Stored supervisor user doc {uid}")

        return uid

    except auth.EmailAlreadyExistsError:
        logger.warning(f"Email {email} already exists — skipping")
        return None
    except Exception as e:
        logger.error(f"Failed to create supervisor {idx}: {e}")
        return None

def create_bus(idx: int, supervisor_uid: str = None):
    plate = random_plate_number()
    bus_num = random_bus_number()

    bus_doc = {
        'plate_number': plate,
        'bus_number': bus_num,
        'capacity': 10,
        'created_at': firestore.SERVER_TIMESTAMP,
        'is_available': True,
        'status': 'active',
        'route_id': None,
        'supervisor_id': None,   # link to supervisor (can be null)
        'updated_at': firestore.SERVER_TIMESTAMP
    }

    # Use plate_number as document ID (unique)
    doc_ref = buses_col.document(plate)
    doc_ref.set(bus_doc)
    logger.info(f"Created bus {bus_num} ({plate}) → supervisor: {supervisor_uid or 'None'}")

# ────────────────────────────────────────────────
# Main execution
# ────────────────────────────────────────────────

print("Cleaning old test supervisors and buses...")

# Delete old test supervisors (email starts with supervisor)
deleted = 0
for doc in users_col.where('email', '>=', 'supervisor').where('email', '<=', 'supervisor\uf8ff').stream():
    uid = doc.to_dict().get('uid')
    if uid:
        try:
            auth.delete_user(uid)
        except:
            pass
    doc.reference.delete()
    deleted += 1

# Delete all buses (for clean test — comment out if you want to keep existing)
for doc in buses_col.stream():
    doc.reference.delete()
    deleted += 1

print(f"Deleted {deleted} old entries.")

print("\nCreating 10 supervisors + 10 buses...")

supervisor_uids = []

for i in range(1, 11):
    uid = create_supervisor(i)
    if uid:
        supervisor_uids.append(uid)
    time.sleep(0.4)  # avoid auth rate limit

# Create 10 buses — assign supervisors randomly (some buses may have no supervisor)
print("\nCreating 10 buses...")
for i in range(1, 11):
    # Randomly assign a supervisor (or None)
    sup_uid = random.choice(supervisor_uids + [None]) if supervisor_uids else None
    create_bus(i, sup_uid)
    time.sleep(0.2)

print("\nDone!")
print("→ 10 supervisors created (check Authentication & users collection)")
print("→ 10 buses created (check buses collection)")
print("You can now run the optimization pipeline.")