package com.example.svg_parsing

import java.util.PriorityQueue
import kotlin.math.pow
import kotlin.math.sqrt

data class Point(
    val id: String,
    val floor: Int,
    val type: String,
    val x: Float,
    val y: Float
)

data class Connection(
    val fromId: String,
    val toId: String
)
data class PathResult(
    val path: List<Point>,
    val totalDistance: Float
)

class NavigationGraph(
    val points: List<Point>,
    val connections: List<Connection>
){
    fun findRoomPoint(room: String): Point? {
        return points.find { point ->
            when {
                point.type == "room" && !point.id.startsWith("p_room_toilet") ->
                    point.id == "p_room_$room"
                else -> false
            }
        }
    }

    fun findBestToiletPath(startRoom: String, isMale: Boolean): PathResult? {
        val toiletType = if (isMale) "M" else "F"
        val startPoint = findRoomPoint(startRoom) ?: return null
        val startFloor = startPoint.floor

        // Ищем все туалеты на ±1 этаже от стартовой точки
        val validFloors = listOf(startFloor - 1, startFloor, startFloor + 1).filter { it in 1..5 }

        val allToilets = points.filter { point ->
            point.floor in validFloors &&
                    point.id.matches(Regex("p_room_toilet\\d+$toiletType"))
        }

        if (allToilets.isEmpty()) return null

        // Логируем все найденные туалеты
        println("Найдено туалетов: ${allToilets.size}")
        allToilets.forEach { toilet ->
            println("Туалет: ${toilet.id}, этаж: ${toilet.floor}")
        }

        // Находим пути ко всем туалетам и логируем их
        val allPaths = allToilets.mapNotNull { toilet ->
            val path = findShortestPath(startPoint.id, toilet.id)
            if (path != null) {
                println("Путь к ${toilet.id}: дистанция=${path.totalDistance}, этажи: ${path.path.map { it.floor }}")
            }
            path
        }

        if (allPaths.isEmpty()) {
            println("Не удалось построить ни одного пути к туалетам")
            return null
        }

        // Выбираем путь с минимальной дистанцией
        val bestPath = allPaths.minByOrNull { it.totalDistance }
        println("Выбран лучший путь: дистанция=${bestPath?.totalDistance} к ${bestPath?.path?.last()?.id}")

        return bestPath
    }

    fun findConnectionsForPoint(pointId: String): List<Connection> {
        return connections.filter { conn ->
            conn.fromId == pointId || conn.toId == pointId
        }
    }

    fun findPath(startRoom: String, endRoom: String): PathResult? {
        val startPoint = findRoomPoint(startRoom) ?: return null
        val endPoint = findRoomPoint(endRoom) ?: return null

        return findShortestPath(startPoint.id, endPoint.id)
    }

    private fun findShortestPath(startId: String, endId: String): PathResult? {
        val distances = mutableMapOf<String, Float>().withDefault { Float.MAX_VALUE }
        val previous = mutableMapOf<String, String?>()
        val visited = mutableSetOf<String>()
        val priorityQueue = PriorityQueue<Pair<String, Float>>(compareBy { it.second })

        distances[startId] = 0f
        priorityQueue.add(startId to 0f)

        while (priorityQueue.isNotEmpty()) {
            val (currentId, currentDist) = priorityQueue.poll()!!

            if (currentId == endId) {
                return reconstructPath(previous, endId, currentDist)
            }

            if (visited.contains(currentId)) continue
            visited.add(currentId)

            val currentPoint = points.find { it.id == currentId } ?: continue

            findConnectionsForPoint(currentId).forEach { connection ->
                val neighborId = if (connection.fromId == currentId) connection.toId else connection.fromId
                val neighborPoint = points.find { it.id == neighborId } ?: return@forEach

                if (currentPoint.floor != neighborPoint.floor &&
                    !(currentPoint.type == "stairs" && neighborPoint.type == "stairs")) {
                    return@forEach
                }

                val distance = calculateDistance(currentPoint, neighborPoint)
                val newDist = currentDist + distance

                if (newDist < distances.getValue(neighborId)) {
                    distances[neighborId] = newDist
                    previous[neighborId] = currentId
                    priorityQueue.add(neighborId to newDist)
                }
            }
        }

        return null
    }

    private fun reconstructPath(
        previous: Map<String, String?>,
        endId: String,
        totalDistance: Float
    ): PathResult {
        val path = mutableListOf<Point>()
        var currentId: String? = endId

        while (currentId != null) {
            points.find { it.id == currentId }?.let { path.add(it) }
            currentId = previous[currentId]
        }

        return PathResult(path.reversed(), totalDistance)
    }

    private fun calculateDistance(p1: Point, p2: Point): Float {
        if (p1.floor != p2.floor) return 50f
        return sqrt((p1.x - p2.x).pow(2) + (p1.y - p2.y).pow(2))
    }
}