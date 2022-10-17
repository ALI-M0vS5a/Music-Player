package com.flammky.musicplayer.library.localsong.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ButtonDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.flammky.androidx.viewmodel.compose.activityViewModel
import com.flammky.common.kotlin.coroutines.safeCollect
import com.flammky.musicplayer.base.compose.rememberContextHelper
import com.flammky.musicplayer.library.localsong.data.LocalSongModel
import com.google.accompanist.placeholder.PlaceholderHighlight
import com.google.accompanist.placeholder.placeholder
import com.google.accompanist.placeholder.shimmer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.math.max
import kotlin.math.min

@Composable
internal fun LocalSongDisplay(
	modifier: Modifier = Modifier,
	viewModel: LocalSongViewModel,
	navigate: (String) -> Unit
) {
	Column(
		modifier = Modifier
			.fillMaxWidth()
			.then(modifier),
		verticalArrangement = Arrangement.spacedBy(8.dp)
	) {
		DisplayHeader()
		DisplayContent(
			list = viewModel.listState.read(),
			onItemClicked = { viewModel.play(it) },
			navigate = navigate
		)
	}
}

@Composable
private fun DisplayHeader(
	modifier: Modifier = Modifier,
	textStyle: TextStyle = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Medium),
) {
	Text(
		modifier = modifier,
		text = "Device Songs", style = textStyle
	)
}

@Composable
private fun DisplayContent(
	modifier: Modifier = Modifier,
	list: List<LocalSongModel>,
	onItemClicked: (item: LocalSongModel) -> Unit,
	navigate: (String) -> Unit
) {
	BoxWithConstraints(modifier = modifier) {
		DynamicDisplayContent(
			list = list,
			constraint = constraints,
			childConstraint = constraints,
			contentSpacer = 10.dp.roundToPx(),
			maxRow = 2,
			rowSpacer = 10.dp.roundToPx(),
			minChild = 3,
			onItemClicked = onItemClicked,
			navigate = navigate
		)
	}
}



// Maybe call it LimitedFlowRow or something

@Composable
private fun DynamicDisplayContent(
	list: List<LocalSongModel>,
	minChild: Int,
	maxChild: Int,
	minWidth: Dp = 0.dp,
	maxWidth: Dp = Dp.Infinity,
	minHeight: Dp = 0.dp,
	maxHeight: Dp = Dp.Infinity,
	minChildWidth: Dp = 0.dp,
	maxChildWidth: Dp = maxWidth,
	minChildHeight: Dp = 0.dp,
	maxChildHeight: Dp = maxHeight,
	contentSpacer: Dp = 0.dp,
	rowSpacer: Dp = 0.dp,
	maximumRow: Int,
	onItemClicked: (item: LocalSongModel) -> Unit,
	navigate: (String) -> Unit
) {
	require(minWidth.value >= 0) {
		"minWidth < 0"
	}
	require(minHeight.value >= 0) {
		"minHeight < 0"
	}
	require(minChildWidth.value >= 0) {
		"minChildWidth < 0"
	}
	require(minChildHeight.value >= 0) {
		"minChildHeight < 0"
	}
	require(minChildWidth >= minWidth) {
		"minChildWidth < minWidth"
	}
	require(minChildHeight >= minHeight) {
		"minChildHeight < minHeight"
	}
	require(maxChildWidth <= maxWidth) {
		"maxChildWidth > maxWidth"
	}
	require(maxChildHeight <= maxHeight) {
		"maxChildHeight > maxHeight"
	}
	DynamicDisplayContent(
		list = list,
		constraint = Constraints(
			minWidth.roundToPx(),
			maxWidth.roundToPx(),
			minHeight.roundToPx(),
			maxHeight.roundToPx()
		),
		childConstraint = Constraints(
			minChildWidth.roundToPx(),
			maxChildWidth.roundToPx(),
			minChildHeight.roundToPx(),
			maxChildHeight.roundToPx()
		),
		contentSpacer = contentSpacer.roundToPx(),
		maxRow = maximumRow,
		rowSpacer = rowSpacer.roundToPx(),
		minChild = minChild,
		onItemClicked = onItemClicked,
		navigate = navigate
	)
}

@Composable
private fun DynamicDisplayContent(
	list: List<LocalSongModel>,
	constraint: Constraints,
	childConstraint: Constraints,
	contentSpacer: Int,
	maxRow: Int,
	rowSpacer: Int,
	minChild: Int,
	onItemClicked: (item: LocalSongModel) -> Unit,
	navigate: (String) -> Unit
) {
	when(list.size) {
		0 -> return // show no local song available
		1, 2, 3 -> Unit // maybe something else
	}

	val contextHelper = rememberContextHelper()

	fun calculateHorizontalSpacer(amount: Int): Float {
		return contentSpacer.toFloat() * if (amount <= 0) 0 else amount - 1
	}

	fun calculateRowSpacer(amount: Int): Float {
		return rowSpacer.toFloat() * if (amount <= 0) 0 else amount - 1
	}

	fun calculateHorizontalPlaceableChild(childWidth: Float): Int {
		val raw = constraint.maxWidth / childWidth
		val spaceConstrained = constraint.maxWidth - calculateHorizontalSpacer(raw.toInt())
		return (spaceConstrained / childWidth).toInt()
	}

	fun calculateVerticalPlaceableChild(childHeight: Float): Int {
		val raw = constraint.maxHeight / childHeight
		val spaceConstrained = constraint.maxHeight - calculateRowSpacer(raw.toInt())
		return (spaceConstrained / childHeight).toInt()
	}

	fun calculateHorizontalContentLeftover(
		childWidth: Float,
		childAmount: Int = calculateHorizontalPlaceableChild(childWidth)
	): Float {
		val spaceConstrained = constraint.maxWidth - calculateHorizontalSpacer(childAmount)
		return (spaceConstrained - childWidth * childAmount)
	}

	fun calculateVerticalContentLeftover(childHeight: Float): Float {
		return constraint.maxHeight - childHeight
	}

	fun expandConstrained(childWidth: Float, childHeight: Float): Float {
		val h = calculateHorizontalContentLeftover(childWidth)
		val v = calculateVerticalContentLeftover(childHeight)
		return if (h < v) {
			childWidth + h / max(calculateHorizontalPlaceableChild(childWidth), 1)
		} else {
			childHeight +	v / max (calculateVerticalPlaceableChild(childHeight), 1)
		}
	}

	fun increaseChildAmount(childWidth: Float, amount: Int): Float {
		if (amount < 0) return childWidth
		val placeable = calculateHorizontalPlaceableChild(childWidth)
		val requestedAmount = placeable + amount
		val spaced = constraint.maxWidth - calculateHorizontalSpacer(requestedAmount)
		val calc = spaced / requestedAmount
		return when {
			calc < constraint.minWidth ||
				calc < constraint.minHeight ||
				calc > constraint.maxWidth ||
				calc > constraint.maxHeight -> childWidth
			else -> calc
		}
	}

	var childSize = minOf(constraint.maxWidth, constraint.maxHeight).toFloat().let { constraints ->
		increaseChildAmount(
			childWidth = constraints,
			amount = minChild - calculateHorizontalPlaceableChild(constraints)
		)
	}

	if (calculateHorizontalContentLeftover(childSize) > 0) {
		childSize = increaseChildAmount(childSize, 1)
	}

	if (calculateVerticalContentLeftover(childSize) < 0) {
		childSize = increaseChildAmount(childSize, 1)
	}

	// TODO: consider child constraints

	val placeable = (constraint.maxWidth / childSize).let { raw ->
		val spaceConstrained = constraint.maxWidth - calculateHorizontalSpacer(raw.toInt())
		spaceConstrained / childSize
	}.toInt()

	val placeableRow = (constraint.maxHeight / childSize).let { raw ->
		val spaceConstrained = constraint.maxHeight - calculateRowSpacer(raw.toInt())
	  spaceConstrained / childSize
	}.toInt()

	Column(
		modifier = Modifier.clip(RoundedCornerShape(5.dp)),
		horizontalAlignment = Alignment.CenterHorizontally,
		verticalArrangement = Arrangement.spacedBy(rowSpacer.toDp())
	) {
		var taken = 0
		repeat(placeableRow) { index ->
			val lastRow = index == placeableRow - 1
			val items =
				if (lastRow) {
					list.drop(taken)
				} else {
					list.drop(taken).take(min(list.size, placeable)).also { taken += it.size }
				}
			DisplayContentRow(
				modifier = Modifier
					.width(constraint.maxWidth.toDp())
					.height(childSize.toDp())
				/*.padding(
					start = contentStartPadding,
					end = contentEndPadding,
					top = contentTopPadding,
					bottom = contentBottomPadding
				)*/,
				horizontalArrangement = Arrangement.spacedBy(contentSpacer.toDp()),
				verticalAlignment = Alignment.CenterVertically,
				isLastRow = lastRow,
				lastIndex = min(items.lastIndex, placeable - 1),
				items = items,
				onItemClicked = onItemClicked,
				navigateSongList = { navigate(LocalSongNavigator.localSongListRoute) }
			)
		}
	}
}

@Composable
private fun DisplayContentRow(
	modifier: Modifier,
	horizontalArrangement: Arrangement.Horizontal,
	verticalAlignment: Alignment.Vertical,
	isLastRow: Boolean,
	lastIndex: Int,
	items: List<LocalSongModel>,
	onItemClicked: (LocalSongModel) -> Unit,
	navigateSongList: () -> Unit
) {

	if (items.isEmpty()) return

	Row(
		modifier = Modifier.then(modifier),
		horizontalArrangement = horizontalArrangement,
		verticalAlignment = verticalAlignment
	) {
		val repeat = lastIndex + 1
		if (!isLastRow) {
			repeat(repeat) { i ->
				DisplayContentItem(items[i], onItemClicked)
			}
		} else {
			repeat(repeat) { i ->
				if (i != lastIndex || items.lastIndex == lastIndex) {
					DisplayContentItem(items[i], onItemClicked)
				} else {
					DisplayLastContentItem(items = items.drop(i), navigateSongList)
				}
			}
		}
	}
}

private val UNSET = Any()
private val LOADING = Any()

@Composable
private fun DisplayContentItem(
	item: LocalSongModel,
	onItemClicked: (item: LocalSongModel) -> Unit
) {
	val viewModel: LocalSongViewModel = activityViewModel()
	val model = remember { mutableStateOf<Any?>(UNSET) }

	Box(
		modifier = Modifier
			.fillMaxHeight()
			.aspectRatio(1f, true)
			.clip(RoundedCornerShape(5.dp))
			.background(Color(0xFFC2C2C2))
			.clickable { onItemClicked(item) }
	) {
		AsyncImage(
			modifier = Modifier
				.fillMaxSize()
				.placeholder(
					visible = model.value == LOADING,
					color = Color(0xFFA0A0A0),
					highlight = PlaceholderHighlight.shimmer(Color.DarkGray)
				),
			model = model.read(),
			contentDescription = "art",
			contentScale = ContentScale.Crop
		)
		DisplayContentItemShadowedDescription(
			modifier = Modifier.align(Alignment.BottomCenter),
			text = item.displayName ?: ""
		)
	}

	val scope = rememberCoroutineScope()
	val coroutineContext = Dispatchers.Main.immediate + SupervisorJob()
	DisposableEffect(key1 = item) {
		val job = scope.launch(coroutineContext) {
			model.overwrite(LOADING)
			viewModel.collectArtwork(item).safeCollect { art ->
				val value = model.value
				if (art is Bitmap && value is Bitmap && art.sameAs(value)) return@safeCollect
				model.overwrite(art)
			}
		}
		onDispose { job.cancel() }
	}
}

@Composable
private fun DisplayContentItemShadowedDescription(
	modifier: Modifier,
	text: String
) {
	val alpha = 0.75F
	val shadowColor = Color.Black
	val textColor = Color.White
	Box(
		modifier = Modifier
			.fillMaxWidth()
			.then(modifier),
		contentAlignment = Alignment.BottomCenter,
	) {
		Column {
			val typography = MaterialTheme.typography.labelMedium
			Spacer(modifier = Modifier.height(2.dp))
			Text(
				modifier = Modifier.fillMaxWidth(0.85f),
				text = text,
				color = textColor,
				style = typography.copy(shadow = Shadow(Color.Black, Offset(1f,1f), 1f)),
				textAlign = TextAlign.Center,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis
			)
			Spacer(modifier = Modifier.height(2.dp))
		}
	}
}

@Composable
private fun DisplayLastContentItem(
	items: List<LocalSongModel>,
	navigate: () -> Unit
) {
	Box(
		modifier = Modifier
			.fillMaxHeight()
			.aspectRatio(1f, true)
			.clip(RoundedCornerShape(5.dp))
			.background(Color(0xFFC2C2C2))
			.clickable(onClick = navigate)
	) {
		Column {
			var i = 0
			repeat(min(2, items.size)) { rowIndex ->
				i += rowIndex
				val f = if (rowIndex == 0) 0.5f else 1f
				Row(modifier = Modifier
					.fillMaxWidth()
					.fillMaxHeight(f)
				) {
					repeat(min(2, items.drop(i).size)) { itemIndex ->
						i += itemIndex
						val model = items[i]
						DisplayLastContentItemChild(model)
					}
				}
			}
		}
		val shadowColor = Color.Black
		Box(
			modifier = Modifier
				.fillMaxSize()
				.background(shadowColor.copy(alpha = 0.7f))
				.padding(bottom = 2.dp)
		) {
			val textColor = Color.White
			Text(
				modifier = Modifier
					.align(Alignment.Center)
					.widthIn(max = 96.dp)
					.padding(horizontal = 2.dp),
				text = "${items.size}",
				color = textColor,
				style = MaterialTheme.typography.titleMedium,
				textAlign = TextAlign.Center,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)
			val typography = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
			Text(
				modifier = Modifier
					.align(Alignment.BottomCenter)
					.fillMaxWidth(0.9f)
					.heightIn(20.dp),
				text = "See more",
				color = textColor,
				style = typography,
				textAlign = TextAlign.Center,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis
			)
		}
	}
}

@Composable
private fun DisplayLastContentItemChild(
	model: LocalSongModel
) {
	val viewModel: LocalSongViewModel = activityViewModel()
	val imageModel = remember { mutableStateOf<Any?>(UNSET) }

	AsyncImage(
		modifier = Modifier
			.fillMaxHeight()
			.aspectRatio(1f, true)
			.placeholder(
				visible = imageModel.value === LOADING,
				color = Color(0xFFA0A0A0)
			),
		model = imageModel.read(),
		contentDescription = "art",
		contentScale = ContentScale.Crop
	)

	val scope = rememberCoroutineScope()
	val coroutineContext = Dispatchers.Main.immediate + SupervisorJob()
	DisposableEffect(key1 = model) {
		val job = scope.launch(coroutineContext) {
			imageModel.overwrite(LOADING)
			viewModel.collectArtwork(model).safeCollect { art ->
				val value = imageModel.value
				if (art is Bitmap && value is Bitmap && art.sameAs(value)) return@safeCollect
				imageModel.overwrite(art)
			}
		}
		onDispose { job.cancel() }
	}
}

@Composable
private fun Dp.roundToPx(density: Density = LocalDensity.current): Int {
	return with(density) { roundToPx() }
}

@Composable
private fun Int.toDp(density: Density = LocalDensity.current): Dp {
	return with(density) { toDp() }
}

@Composable
private fun Float.toDp(density: Density = LocalDensity.current): Dp {
	Timber.d("$density")
	return with(density) { toDp() }
}

// is explicit read like this better ?
private fun <T> State<T>.read(): T {
	return value
}

// is explicit write like this better ?
private fun <T> MutableState<T>.overwrite(value: T) {
	this.value = value
}

// is explicit write like this better ?
private fun <T> MutableState<T>.rewrite(block: (T) -> T) {
	this.value = block(this.value)
}
