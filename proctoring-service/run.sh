#!/bin/bash

# Proctoring Service Quick Start Script
# Usage: bash run.sh [dev|prod]

set -e

MODE=${1:-dev}

echo "🎥 Starting Proctoring Service (Mode: $MODE)"
echo "=================================================="

# Check Python version
PYTHON_VERSION=$(python --version 2>&1 | awk '{print $2}')
echo "Python Version: $PYTHON_VERSION"

# Create virtual environment if it doesn't exist
if [ ! -d "venv" ]; then
    echo "📦 Creating virtual environment..."
    python -m venv venv
fi

# Activate virtual environment
echo "🔧 Activating virtual environment..."
source venv/bin/activate

# Install dependencies
echo "📥 Installing dependencies..."
pip install --quiet -r requirements.txt

# Copy .env if it doesn't exist
if [ ! -f ".env" ]; then
    echo "⚙️  Creating .env from template..."
    cp .env.example .env
fi

echo ""
echo "✅ Setup complete!"
echo "=================================================="
echo ""

# Run based on mode
if [ "$MODE" = "dev" ]; then
    echo "🚀 Running in DEVELOPMENT mode (auto-reload enabled)"
    echo "📖 API Docs: http://localhost:8000/docs"
    echo ""
    uvicorn app.main:app --reload --port 8000
else
    echo "🚀 Running in PRODUCTION mode"
    echo "📖 API Docs: http://localhost:8000/docs"
    echo ""
    uvicorn app.main:app --host 0.0.0.0 --port 8000 --workers 4
fi
