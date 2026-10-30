package com.mindunlock.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mindunlock.app.ui.screens.ChapterListScreen
import com.mindunlock.app.ui.screens.ChapterReaderScreen
import com.mindunlock.app.ui.screens.ConnectScreen
import com.mindunlock.app.ui.theme.MindUnlockTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as MindUnlockApp

        setContent {
            MindUnlockTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    val repository = app.repository

                    NavHost(
                        navController = navController,
                        startDestination = "chapters"
                    ) {
                        composable("connect") {
                            ConnectScreen(
                                repository = repository,
                                onSynced = {
                                    navController.navigate("chapters") {
                                        popUpTo("connect") { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable("chapters") {
                            ChapterListScreen(
                                repository = repository,
                                onChapterClick = { id ->
                                    navController.navigate("reader/$id")
                                },
                                onOpenConnect = {
                                    navController.navigate("connect")
                                }
                            )
                        }
                        composable(
                            route = "reader/{chapterId}",
                            arguments = listOf(navArgument("chapterId") { type = NavType.StringType })
                        ) { backStack ->
                            val chapterId = backStack.arguments?.getString("chapterId") ?: return@composable
                            ChapterReaderScreen(
                                chapterId = chapterId,
                                repository = repository,
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
