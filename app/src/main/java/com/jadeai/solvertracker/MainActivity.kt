package com.jadeai.solvertracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.jadeai.solvertracker.ui.navigation.SolverTrackerNavGraph
import com.jadeai.solvertracker.ui.theme.SolverTrackerTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SolverTrackerTheme {
                SolverTrackerNavGraph()
            }
        }
    }
}
