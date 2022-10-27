// MIT License
//
// Copyright (c) 2022 被风吹过的夏天
//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all
// copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
// SOFTWARE.

package com.melody.map.tencent_compose

import androidx.compose.runtime.*
import com.melody.map.tencent_compose.adapter.ComposeInfoWindowAdapter
import com.melody.map.tencent_compose.overlay.DragState
import com.melody.map.tencent_compose.overlay.MarkerNode
import com.melody.map.tencent_compose.overlay.PolygonNode
import com.melody.map.tencent_compose.overlay.PolylineNode
import com.melody.map.tencent_compose.utils.fastFirstOrNull
import com.tencent.tencentmap.mapsdk.maps.MapView
import com.tencent.tencentmap.mapsdk.maps.TencentMap
import com.tencent.tencentmap.mapsdk.maps.TencentMap.OnInfoWindowClickListener
import com.tencent.tencentmap.mapsdk.maps.model.Marker
import com.tencent.tencentmap.mapsdk.maps.model.Polygon
import com.tencent.tencentmap.mapsdk.maps.model.Polyline

internal interface MapNode {
    fun onAttached() {}
    fun onRemoved() {}
    fun onCleared() {}
}

private object MapNodeRoot : MapNode

internal class MapApplier(
    val map: TencentMap,
    private val mapView: MapView,
) : AbstractApplier<MapNode>(MapNodeRoot) {

    private val decorations = mutableListOf<MapNode>()

    init {
        attachClickListeners()
    }

    override fun onClear() {
        map.clear()
        decorations.forEach { it.onCleared() }
        decorations.clear()
    }

    override fun insertBottomUp(index: Int, instance: MapNode) {
        decorations.add(index, instance)
        instance.onAttached()
    }

    override fun insertTopDown(index: Int, instance: MapNode) {
        // insertBottomUp is preferred
    }

    override fun move(from: Int, to: Int, count: Int) {
        decorations.move(from, to, count)
    }

    override fun remove(index: Int, count: Int) {
        repeat(count) {
            decorations[index + it].onRemoved()
        }
        decorations.remove(index, count)
    }

    private fun attachClickListeners() {
        // 设置Marker的点击事件，return true拦截
        map.setOnMarkerClickListener { marker ->
            // 优先处理普通Marker的事件，不匹配，再去查找轨迹移动的Marker
            /*decorations.nodeForMarker(marker)?.onMarkerClick?.invoke(marker)?:
            (decorations.nodeForMovingPointOverlay(marker)?.onMarkerClick?.invoke(marker)?: false)*/
            decorations.nodeForMarker(marker)?.onMarkerClick?.invoke(marker)?:false
        }
        // Polyline的点击事件
        map.setOnPolylineClickListener { polyline, _ ->
            decorations.nodeForPolyline(polyline)?.onPolylineClick?.invoke(polyline)
        }
        // 弹出的InfoWindow的点击事件
        map.setOnInfoWindowClickListener(object :OnInfoWindowClickListener {
            override fun onInfoWindowClick(marker: Marker?) {
                if(null != marker) {
                    decorations.nodeForMarker(marker)?.onInfoWindowClick?.invoke(marker)
                }
            }
            override fun onInfoWindowClickLocation(p0: Int, p1: Int, p2: Int, p3: Int) {
            }
        })
        // MultiPointOverlay的点击事件
        /*map.setOnMultiPointClickListener { multiPointItem ->
            val node = decorations.nodeForMultiPoint(multiPointItem)
            if(null != node) {
                node.onPointItemClick.invoke(multiPointItem)
                return@setOnMultiPointClickListener true
            }
            return@setOnMultiPointClickListener false
        }*/
        // 多边形点击事件
        map.setOnPolygonClickListener { polygon, _ ->
            decorations.nodeForPolygon(polygon)?.onClick?.invoke(polygon)
        }
        // 长按触发
        map.setOnMarkerDragListener(object : TencentMap.OnMarkerDragListener {
            override fun onMarkerDrag(marker: Marker) {
                with(decorations.nodeForMarker(marker)) {
                    this?.markerState?.position = marker.position
                    this?.markerState?.dragState = DragState.DRAG
                }
            }

            override fun onMarkerDragEnd(marker: Marker) {
                with(decorations.nodeForMarker(marker)) {
                    this?.markerState?.position = marker.position
                    this?.markerState?.dragState = DragState.END
                }
            }

            override fun onMarkerDragStart(marker: Marker) {
                with(decorations.nodeForMarker(marker)) {
                    this?.markerState?.position = marker.position
                    this?.markerState?.dragState = DragState.START
                }
            }
        })
        // 设置InfoWindow内容
        map.setInfoWindowAdapter(
            ComposeInfoWindowAdapter(
                mapView,
                markerNodeFinder = {
                    decorations.nodeForMarker(it)
                }
            )
        )
    }
}
/**
 * Marker
 */
private fun MutableList<MapNode>.nodeForMarker(marker: Marker): MarkerNode? =
    fastFirstOrNull { it is MarkerNode && it.marker.options.title == marker.options.title && it.marker.options.snippet == marker.options.snippet } as? MarkerNode

///**
// * MovingPointOverlay轨迹移动
// */
//private fun MutableList<MapNode>.nodeForMovingPointOverlay(marker: Marker): MovingPointOverlayNode? =
//    fastFirstOrNull { it is MovingPointOverlayNode && it.marker.`object` == marker.`object` } as? MovingPointOverlayNode
//
/**
 * Polyline
 */
private fun MutableList<MapNode>.nodeForPolyline(polyline: Polyline): PolylineNode? =
    fastFirstOrNull { it is PolylineNode && it.polyline == polyline } as? PolylineNode

/**
 * Polygon
 */
private fun MutableList<MapNode>.nodeForPolygon(polygon: Polygon): PolygonNode? =
    fastFirstOrNull { it is PolygonNode && it.polygon == polygon } as? PolygonNode


///**
// * RoutePlanOverlay
// */
//private fun MutableList<MapNode>.nodeForRoutePlanPolyline(polyline: Polyline): RoutePlanOverlayNode? =
//    fastFirstOrNull { it is RoutePlanOverlayNode && null != it.routePlanOverlay?.allPolyLines?.fastFirstOrNull { child -> child == polyline } } as? RoutePlanOverlayNode
//
///**
// * MultiPointOverlay
// */
//private fun MutableList<MapNode>.nodeForMultiPoint(multiPointItem: MultiPointItem): MultiPointOverlayNode? =
//    fastFirstOrNull { it is MultiPointOverlayNode && null != it.multiPointOverlay.items.fastFirstOrNull { child -> child == multiPointItem  } } as? MultiPointOverlayNode
