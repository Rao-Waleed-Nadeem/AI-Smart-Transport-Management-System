# AI Smart Transport Management System

**An Intelligent University Bus Routing & Tracking System**  
**Final Year Project (FYP) | FAST-NUCES Faisalabad | Spring 2026**

A complete role-based Android application with Firebase backend and Python-powered route optimization for university bus transport.

![Project Banner](https://via.placeholder.com/800x300?text=AI+Smart+Transport+System)  
*(Replace with actual screenshot later)*

## ✨ Features

### Student Module
- Seat registration with stop selection on map
- Real-time bus tracking
- View optimized route & estimated time
- Fee calculation and payment status
- Personal attendance history

### Supervisor Module
- View assigned bus and route
- Live GPS location sharing
- Digital attendance marking (RecyclerView + checkboxes)
- Route visualization on map

### Admin Module
- Add buses, stops, and supervisors
- Assign supervisors to buses/routes
- Generate optimized routes (triggers Python backend)
- View all students, attendance, and fees
- Toggle route optimization mode

### Core Technical Features
- Role-based authentication (Student / Supervisor / Admin)
- Real-time bus tracking using Firebase
- Optimized bus routes using TSP + road network
- Fee calculation based on distance
- OSMdroid map with custom teardrop markers (RouteMapActivity)
- Clean MVVM architecture with Repository pattern

## 🛠 Tech Stack

### Android App
- **Language**: Kotlin
- **Architecture**: MVVM + Repository Pattern + ViewModel + LiveData
- **UI**: XML Layouts + Material Design
- **Maps**: OSMdroid (offline-friendly) + Custom pin markers
- **Backend**: Firebase Authentication + Firestore
- **Async**: Kotlin Coroutines

### Python Backend (Route Optimization)
- Clustering (K-Means)
- Traveling Salesman Problem (OR-Tools / Nearest Neighbor)
- Road snapping & main road filtering
- Ready to integrate with Firebase

### Database
- Firebase Firestore (Collections: users, students, supervisors, buses, stops, routes, attendance, tracking, fees)

## 📁 Project Structure

```bash
AI-Smart-Transport-Management-System/
├── app/                    ← Main Android module
│   ├── src/main/java/...   ← Kotlin code (ViewModels, Repositories, Activities)
│   ├── src/main/res/       ← XML layouts & resources
│   └── build.gradle
├── backend/                ← Python route optimization service
│   ├── app.py
│   ├── tsp_solver.py
│   ├── clustering.py
│   ├── routing.py
│   └── requirements.txt
├── docs/                   ← Documentation
│   ├── SRS.txt
│   ├── AI_SmartTransport_Architecture_Guide.md
│   └── python_route_optimization.txt
├── README.md
└── .gitignore