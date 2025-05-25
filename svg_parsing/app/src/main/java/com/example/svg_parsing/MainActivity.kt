package com.example.svg_parsing

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {
    private lateinit var navigationGraph: NavigationGraph
    private lateinit var mapView: MapView
    private lateinit var startRoomSpinner: TextView
    private lateinit var endRoomSpinner: TextView
    private val floorButtons = mutableListOf<Button>()
    private var currentFloor = 2
    private val routeSegments = mutableListOf<Pair<Point, Point>>()
    private var currentSegmentStart: Point? = null
    private var currentPathResult: PathResult? = null
    private var roomList = mutableListOf<String>()
    private var startRoomDialog: Dialog? = null
    private var endRoomDialog: Dialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mapView = findViewById(R.id.MapView)
        startRoomSpinner = findViewById(R.id.startRoomInput)
        endRoomSpinner = findViewById(R.id.endRoomInput)

        // Initialize as TextView since we changed them in layout
        startRoomSpinner = findViewById(R.id.startRoomInput)
        endRoomSpinner = findViewById(R.id.endRoomInput)

        loadFloor(currentFloor)
        initFloorButtons()
        setupFindPathButton()

        CoroutineScope(Dispatchers.IO).launch {
            navigationGraph = SvgParser().parseAllFloors(assets)
            // Extract room numbers from points
            roomList = navigationGraph.points
                .filter { it.type == "room" && !it.id.startsWith("p_room_toilet") }
                .map { it.id.removePrefix("p_room_") }
                .distinct()
                .toMutableList()
            roomList.add("Мужской туалет")
            roomList.add("Женский туалет")
            roomList.sort()
            withContext(Dispatchers.Main) {
                updateFloorButtons()
                setupRoomSpinners()
            }
        }
    }

    private fun setupRoomSpinners() {
        startRoomSpinner.setOnClickListener { showRoomDialog(isStartRoom = true) }
        endRoomSpinner.setOnClickListener { showRoomDialog(isStartRoom = false) }
    }

    private fun showRoomDialog(isStartRoom: Boolean) {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.layout_searchable_spinner)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.WHITE))

        val editText = dialog.findViewById<EditText>(R.id.editText_of_searchableSpinner)
        val listView = dialog.findViewById<ListView>(R.id.listView_of_searchableSpinner)

        // Фильтруем список комнат для начальной точки
        val filteredRooms = if (isStartRoom) {
            roomList.filter { it != "Мужской туалет" && it != "Женский туалет" }
        } else {
            roomList
        }

        val adapter = ArrayAdapter(
            this,
            androidx.appcompat.R.layout.support_simple_spinner_dropdown_item,
            filteredRooms
        )
        listView.adapter = adapter

        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                adapter.filter.filter(s)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        listView.setOnItemClickListener { _, _, position, _ ->
            val selected = adapter.getItem(position)
            if (isStartRoom) {
                startRoomSpinner.text = selected
            } else {
                endRoomSpinner.text = selected
            }
            dialog.dismiss()
        }

        dialog.show()
    }



    private fun initFloorButtons() {
        floorButtons.add(findViewById(R.id.btnFloor1))
        floorButtons.add(findViewById(R.id.btnFloor2))
        floorButtons.add(findViewById(R.id.btnFloor3))
        floorButtons.add(findViewById(R.id.btnFloor4))
        floorButtons.add(findViewById(R.id.btnFloor5))

        floorButtons.forEachIndexed { index, button ->
            button.setOnClickListener {
                switchFloor(index + 1)
            }
        }
    }

    private fun setupFindPathButton() {
        findViewById<Button>(R.id.findPathButton).setOnClickListener {
            val startRoom = startRoomSpinner.text.toString()
            val endRoom = endRoomSpinner.text.toString()

            if (startRoom.isNotEmpty() && endRoom.isNotEmpty()) {
                findAndDisplayPath(startRoom, endRoom)
            } else {
                Toast.makeText(this, "Please select both start and end rooms", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun displayPath(pathResult: PathResult) {
        updateRouteOnMap(pathResult)
    }

    private fun updateRouteOnMap(pathResult: PathResult) {
        mapView.clearMarkers()
        mapView.clearRoutes()

        val currentFloorPoints = pathResult.path.filter { it.floor == currentFloor }
        if (currentFloorPoints.isEmpty()) return

        currentFloorPoints.forEach { point ->
            mapView.addMarker(MapView.Marker(
                x = point.x / mapView.svgWidth,
                y = point.y / mapView.svgHeight,
                name = ""
            ))
        }

        for (i in 0 until pathResult.path.size - 1) {
            val current = pathResult.path[i]
            val next = pathResult.path[i + 1]

            if (current.floor == currentFloor && next.floor == currentFloor) {
                if (currentSegmentStart == null) {
                    currentSegmentStart = current
                }
                routeSegments.add(current to next)
            } else {
                currentSegmentStart = null
            }
        }

        routeSegments.forEach { (start, end) ->
            val startIndex = currentFloorPoints.indexOfFirst { it.id == start.id }
            val endIndex = currentFloorPoints.indexOfFirst { it.id == end.id }

            if (startIndex != -1 && endIndex != -1) {
                mapView.addRoute(MapView.Route(
                    startId = startIndex,
                    endId = endIndex
                ))
            }
        }

        pathResult.path.forEachIndexed { index, point ->
            if (point.floor == currentFloor && point.type == "stairs") {
                val prev = pathResult.path.getOrNull(index - 1)
                val next = pathResult.path.getOrNull(index + 1)

                if ((prev != null && prev.floor != currentFloor) ||
                    (next != null && next.floor != currentFloor)) {
                    val pointIndex = currentFloorPoints.indexOfFirst { it.id == point.id }
                    if (pointIndex != -1) {
                        mapView.addMarker(MapView.Marker(
                            x = point.x / mapView.svgWidth,
                            y = point.y / mapView.svgHeight,
                            name = ""
                        ))
                    }
                }
            }
        }
    }

    private fun switchFloor(newFloor: Int) {
        if (newFloor == currentFloor) return

        currentFloor = newFloor
        loadFloor(currentFloor)
        highlightCurrentFloor()

        currentPathResult?.let { pathResult ->
            updateRouteOnMap(pathResult)
        }
    }

    private fun findAndDisplayPath(startRoom: String, endRoom: String) {
        // Проверяем, не является ли стартовая точка туалетом
        if (startRoom == "Мужской туалет" || startRoom == "Женский туалет") {
            Toast.makeText(this, "Стартовая точка не может быть туалетом", Toast.LENGTH_SHORT).show()
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            val pathResult = when {
                endRoom == "Мужской туалет" -> {
                    navigationGraph.findBestToiletPath(
                        startRoom = startRoom,
                        isMale = true
                    )
                }
                endRoom == "Женский туалет" -> {
                    navigationGraph.findBestToiletPath(
                        startRoom = startRoom,
                        isMale = false
                    )
                }
                else -> {
                    navigationGraph.findPath(startRoom, endRoom)
                }
            }

            currentPathResult = pathResult

            withContext(Dispatchers.Main) {
                if (pathResult != null) {
                    displayPath(pathResult)
                    switchFloor(pathResult.path.first().floor)
                } else {
                    val message = when (endRoom) {
                        "Мужской туалет" -> "Мужской туалет не найден"
                        "Женский туалет" -> "Женский туалет не найден"
                        else -> "Путь не найден"
                    }
                    Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updateFloorButtons() {
        floorButtons.forEachIndexed { index, button ->
            val hasPoints = navigationGraph.points.any { it.floor == index + 1 }
            button.isEnabled = hasPoints
            button.alpha = if (hasPoints) 1f else 0.5f
        }
        highlightCurrentFloor()
    }

    private fun highlightCurrentFloor() {
        floorButtons.forEachIndexed { index, button ->
            if (index + 1 == currentFloor) {
                button.setBackgroundResource(R.drawable.bg_floor_button_selected)
                button.setTextColor(Color.WHITE)
            } else {
                button.setBackgroundResource(R.drawable.bg_floor_button)
                button.setTextColor(Color.WHITE)
            }
        }
    }

    private fun loadFloor(floorNumber: Int) {
        mapView.loadSVGFromAssets("floor_$floorNumber.svg")
        mapView.clearMarkers()
        mapView.clearRoutes()
    }
}