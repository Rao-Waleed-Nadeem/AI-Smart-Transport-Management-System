# main.py (entry point)
import uvicorn 

if __name__ == "__main__":
    uvicorn.run(
        "api.main:app",
        host="0.0.0.0",          # Accessible from network (Kotlin app)
        port=8000,
        reload=True,             # Auto-reload on code change (dev only)
        log_level="info"
    )