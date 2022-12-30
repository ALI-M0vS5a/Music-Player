package com.flammky.musicplayer.user.nav.compose

import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.compose.composable
import com.flammky.musicplayer.base.nav.compose.ComposeRootDestination
import com.flammky.musicplayer.base.nav.compose.ComposeRootNavigator
import com.flammky.musicplayer.user.R
import com.flammky.musicplayer.user.ui.compose.User

object UserRootNavigator : ComposeRootNavigator() {

	private val rootDestination = ComposeRootDestination(
		routeID = "root_user",
		label = "User",
		iconResource = ComposeRootDestination.IconResource
			.ResID(R.drawable.user_circle_outlined_base_512_24),
		selectedIconResource = ComposeRootDestination.IconResource
			.ResID(R.drawable.user_circle_filled_base_512_24)
	)

	override fun getRootDestination(): ComposeRootDestination {
		return rootDestination
	}

	override fun navigateToRoot(
		controller: NavController,
		navOptionsBuilder: NavOptionsBuilder.() -> Unit
	) {
		controller.navigate(rootDestination.routeID, navOptionsBuilder)
	}

	override fun addRootDestination(navGraphBuilder: NavGraphBuilder, controller: NavController) {
		navGraphBuilder.composable(rootDestination.routeID) {
			User {
				controller.navigate(it) {
					popUpTo(rootDestination.routeID) {
						inclusive = controller.graph.findStartDestination().route != rootDestination.routeID
					}
				}
			}
		}
	}
}
