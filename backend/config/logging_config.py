# config/logging_config.py
from loguru import logger
import sys
import os

# Create logs folder if not exists
os.makedirs("logs", exist_ok=True)

# Remove default sink and add our own
logger.remove()

# Console output (colorful)
logger.add(
    sys.stderr,
    format="<green>{time:YYYY-MM-DD HH:mm:ss}</green> | <level>{level: <8}</level> | <cyan>{name}</cyan>:<cyan>{function}</cyan>:<cyan>{line}</cyan> - <level>{message}</level>",
    level="INFO",
    colorize=True
)

# File output (detailed, rotated)
logger.add(
    "logs/app_{time:YYYY-MM-DD}.log",
    rotation="500 MB",
    retention="10 days",
    level="DEBUG",
    encoding="utf8",
    serialize=False  # human readable
)

# Optional: If you later add Sentry
# import sentry_sdk
# sentry_sdk.init(dsn="your-sentry-dsn-here", traces_sample_rate=1.0)

# Export logger so other files can use it
__all__ = ["logger"]