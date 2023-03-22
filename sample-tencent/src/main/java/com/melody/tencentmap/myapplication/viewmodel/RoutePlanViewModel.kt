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

package com.melody.tencentmap.myapplication.viewmodel

import com.melody.sample.common.base.BaseViewModel
import com.melody.tencentmap.myapplication.contract.RoutePlanContract
import com.melody.tencentmap.myapplication.model.DrivingRouteDataState
import com.melody.tencentmap.myapplication.repo.RoutePlanRepository
import com.tencent.tencentmap.mapsdk.maps.model.AnimationListener
import com.tencent.tencentmap.mapsdk.maps.model.LatLng
import kotlinx.coroutines.Dispatchers

/**
 * RoutePlanViewModel
 * @author 被风吹过的夏天
 * @email developer_melody@163.com
 * @github: https://github.com/TheMelody/OmniMap
 * created 2023/02/17 16:42
 */
class RoutePlanViewModel :
    BaseViewModel<RoutePlanContract.Event, RoutePlanContract.State, RoutePlanContract.Effect>(),
    AnimationListener {

    override fun createInitialState(): RoutePlanContract.State {
        return RoutePlanContract.State(
            isLoading = false,
            fromPoint = LatLng(30.558987,103.951446),
            toPoint = LatLng(30.710472,104.106445),
            uiSettings = RoutePlanRepository.initMapUiSettings(),
            mapProperties = RoutePlanRepository.initMapProperties(),
            routePlanDataState = null
        )
    }

    override fun handleEvents(event: RoutePlanContract.Event) {
        when(event) {
            is RoutePlanContract.Event.RoadTrafficClick -> {
                setState { copy(mapProperties = mapProperties.copy(isTrafficEnabled = !mapProperties.isTrafficEnabled)) }
            }
            is RoutePlanContract.Event.QueryRoutePlan -> {
                asyncLaunch(Dispatchers.IO) {
                    setState { copy(isLoading = true, routePlanDataState = null) }
                    val drivingRoutePlanResult = kotlin.runCatching {
                        RoutePlanRepository.queryRoutePlan(
                            queryType = event.queryType,
                            fromPoint = currentState.fromPoint,
                            toPoint = currentState.toPoint
                        )
                    }
                    setState { copy(isLoading = false) }
                    if(drivingRoutePlanResult.isSuccess) {
                        setState {
                            copy(
                                routePlanDataState = drivingRoutePlanResult.getOrNull()?.apply {
                                    polylineAnim?.animationListener = this@RoutePlanViewModel
                                }
                            )
                        }
                    }else{
                        setEffect { RoutePlanContract.Effect.Toast(drivingRoutePlanResult.exceptionOrNull()?.message) }
                    }
                }
            }
        }
    }

    fun queryRoutePlan(queryType: Int = 0) {
        setEvent(RoutePlanContract.Event.QueryRoutePlan(queryType))
    }

    fun switchRoadTraffic() {
        setEvent(RoutePlanContract.Event.RoadTrafficClick)
    }

    override fun onAnimationStart() {
        val dataState = currentState.routePlanDataState
        if(dataState is DrivingRouteDataState) {
            setState {
                copy(routePlanDataState = dataState.copy(isAnimationStart = true))
            }
        }
    }

    override fun onAnimationEnd() {
        val dataState = currentState.routePlanDataState
        if(dataState is DrivingRouteDataState) {
            setState {
                copy(routePlanDataState = dataState.copy(isAnimationEnd = true))
            }
        }
    }
}