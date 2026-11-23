package com.timetable.shared.data.repository

/**
 * Called after timetable data changes (cache restore or remote refresh) so each
 * platform can poke its ambient surfaces: Wear refreshes complications + tiles
 * widgets; phone refreshes the Glance home-screen widget.
 */
interface ScheduleUpdateListener {
    fun onScheduleUpdated()
}
