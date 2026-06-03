@file:OptIn(InternalResourceApi::class)

package zune.composeapp.generated.resources

import kotlin.OptIn
import kotlin.String
import kotlin.collections.MutableMap
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.InternalResourceApi
import org.jetbrains.compose.resources.ResourceContentHash
import org.jetbrains.compose.resources.ResourceItem

private const val MD: String = "composeResources/zune.composeapp.generated.resources/"

@delegate:ResourceContentHash(828_697_762)
internal val Res.drawable.selector_download: DrawableResource by lazy {
      DrawableResource("drawable:selector_download", setOf(
        ResourceItem(setOf(), "${MD}drawable/selector_download.xml", -1, -1),
      ))
    }

@delegate:ResourceContentHash(-1_064_406_652)
internal val Res.drawable.selector_favorite_check_box: DrawableResource by lazy {
      DrawableResource("drawable:selector_favorite_check_box", setOf(
        ResourceItem(setOf(), "${MD}drawable/selector_favorite_check_box.xml", -1, -1),
      ))
    }

@delegate:ResourceContentHash(-1_545_386_005)
internal val Res.drawable.shapebgsearch: DrawableResource by lazy {
      DrawableResource("drawable:shapebgsearch", setOf(
        ResourceItem(setOf(), "${MD}drawable/shapebgsearch.xml", -1, -1),
      ))
    }

@delegate:ResourceContentHash(546_827_266)
internal val Res.drawable.transparent_rect: DrawableResource by lazy {
      DrawableResource("drawable:transparent_rect", setOf(
        ResourceItem(setOf(), "${MD}drawable/transparent_rect.xml", -1, -1),
      ))
    }

@delegate:ResourceContentHash(1_015_981_842)
internal val Res.drawable.transparent_toolbar: DrawableResource by lazy {
      DrawableResource("drawable:transparent_toolbar", setOf(
        ResourceItem(setOf(), "${MD}drawable/transparent_toolbar.xml", -1, -1),
      ))
    }

@delegate:ResourceContentHash(-1_808_006_715)
internal val Res.drawable.widget_selector: DrawableResource by lazy {
      DrawableResource("drawable:widget_selector", setOf(
        ResourceItem(setOf(), "${MD}drawable/widget_selector.xml", -1, -1),
      ))
    }

@InternalResourceApi
internal fun _collectCommonMainDrawable1Resources(map: MutableMap<String, DrawableResource>) {
  map.put("selector_download", Res.drawable.selector_download)
  map.put("selector_favorite_check_box", Res.drawable.selector_favorite_check_box)
  map.put("shapebgsearch", Res.drawable.shapebgsearch)
  map.put("transparent_rect", Res.drawable.transparent_rect)
  map.put("transparent_toolbar", Res.drawable.transparent_toolbar)
  map.put("widget_selector", Res.drawable.widget_selector)
}
