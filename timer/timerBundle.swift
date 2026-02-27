//
//  timerBundle.swift
//  timer
//
//  Created by Marco on 26/2/2026.
//

import WidgetKit
import SwiftUI

@main
struct timerBundle: WidgetBundle {
    var body: some Widget {
        timer()
        timerControl()
        timerLiveActivity()
    }
}
