# scripts/delete_all_auth_users.py
"""
WARNING: This script PERMANENTLY DELETES ALL USERS from Firebase Authentication.
Use only for testing / development environments.
There is NO UNDO.

It also optionally deletes corresponding documents in 'users' collection.
"""

import sys
import os
import time

# Add project root to sys.path
sys.path.append(os.path.abspath(os.path.join(os.path.dirname(__file__), '..')))

from firebase_admin import auth, firestore
from database.firebase import get_collection
from config.logging_config import logger

users_col = get_collection('users')

def delete_all_auth_users():
    print("WARNING: This will DELETE ALL Firebase Authentication users.")
    confirm = input("Type YES to continue (case-sensitive): ").strip()
    
    if confirm != "YES":
        print("Aborted.")
        return

    print("\nStarting deletion...\n")

    deleted_count = 0
    page_token = None

    while True:
        try:
            # List users (100 at a time)
            result = auth.list_users(page_token=page_token)
            users = result.users

            if not users:
                break

            for user in users:
                uid = user.uid
                email = user.email or "unknown"

                try:
                    # Delete from Authentication
                    auth.delete_user(uid)
                    logger.info(f"Deleted auth user: {uid} ({email})")
                    deleted_count += 1

                    # Optional: Delete from 'users' collection
                    user_doc = users_col.document(uid)
                    if user_doc.get().exists:
                        user_doc.delete()
                        logger.debug(f"Deleted users doc: {uid}")

                except Exception as e:
                    logger.error(f"Failed to delete user {uid}: {e}")

            # Get next page
            page_token = result.page_token

            # Small delay to avoid rate limits
            time.sleep(0.5)

        except Exception as e:
            logger.error(f"Error listing users: {e}")
            break

    print(f"\nFinished.")
    print(f"Total users deleted from Authentication: {deleted_count}")
    print("Check Firebase Console → Authentication to confirm.")

if __name__ == "__main__":
    delete_all_auth_users()