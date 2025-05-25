package com.example.svg_parsing

import android.content.res.AssetManager
import org.w3c.dom.Document
import org.w3c.dom.Element
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.pow

class SvgParser {
    private val allPoints = mutableListOf<Point>()
    private val allConnections = mutableListOf<Connection>()
    private val floorFiles = listOf(
        "floor_1.svg" to 1,
        "floor_2.svg" to 2,
        "floor_3.svg" to 3,
        "floor_4.svg" to 4,
        "floor_5.svg" to 5
    )

    fun parseAllFloors(assetManager: AssetManager): NavigationGraph {
        allPoints.clear()
        allConnections.clear()

        floorFiles.forEach { (fileName, floorNumber) ->
            try {
                parseSingleFloor(assetManager, fileName, floorNumber)
            } catch (e: Exception) {
                println("Файл $fileName не найден, этаж $floorNumber пропущен")
            }
        }

        connectStairsBetweenAllFloors()

        return NavigationGraph(allPoints.toList(), allConnections.toList())
    }

    private fun parseSingleFloor(assetManager: AssetManager, fileName: String, floorNumber: Int) {
        val inputStream = assetManager.open(fileName)
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(inputStream)
        inputStream.close()

        val points = parsePoints(doc, floorNumber)
        val connections = parseConnections(doc, points)

        allPoints.addAll(points)
        allConnections.addAll(connections)
    }

    private fun parsePoints(doc: Document, floor: Int): List<Point> {
        val points = mutableListOf<Point>()
        val circles = doc.getElementsByTagName("ellipse")

        for (i in 0 until circles.length) {
            val circle = circles.item(i) as Element
            val id = circle.getAttribute("inkscape:label")

            val type = when {
                id.startsWith("p_room_") -> "room"
                id.startsWith("p_stairs_") -> "stairs"
                else -> "point"
            }

            points.add(
                Point(
                    id = if (id.startsWith("p_node_")) id + floor else id,
                    floor = floor,
                    x = circle.getAttribute("cx").toFloat(),
                    y = circle.getAttribute("cy").toFloat(),
                    type = type
                )
            )
        }
        return points
    }

    private fun parseConnections(doc: Document, points: List<Point>): List<Connection> {
        val connections = mutableListOf<Connection>()
        val lines = doc.getElementsByTagName("line")

        for (i in 0 until lines.length) {
            val line = lines.item(i) as Element
            val x1 = line.getAttribute("x1").toFloat()
            val y1 = line.getAttribute("y1").toFloat()
            val x2 = line.getAttribute("x2").toFloat()
            val y2 = line.getAttribute("y2").toFloat()

            val fromPoint = findNearestPoint(x1, y1, points)
            val toPoint = findNearestPoint(x2, y2, points)

            if (fromPoint != null && toPoint != null) {
                connections.add(Connection(fromPoint.id, toPoint.id))
            }
        }
        return connections
    }

    private fun connectStairsBetweenAllFloors() {
        val stairsGroups = allPoints
            .filter { it.type == "stairs" }
            .groupBy { point -> point.id.takeLast(1) }


        stairsGroups.values.forEach { stairsInGroup ->
            val sortedStairs = stairsInGroup.sortedBy { it.floor }

            for (i in 0 until sortedStairs.size - 1) {
                val current = sortedStairs[i]
                val next = sortedStairs[i + 1]

                if (next.floor == current.floor + 1) {
                    allConnections.add(Connection(current.id, next.id))
                }
            }
        }
    }

    private fun findNearestPoint(x: Float, y: Float, points: List<Point>): Point? {
        return points.minByOrNull { point ->
            kotlin.math.sqrt(
                (x - point.x).pow(2) + (y - point.y).pow(2)
            )
        }
    }
}