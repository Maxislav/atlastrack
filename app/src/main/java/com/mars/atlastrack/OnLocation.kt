package com.mars.atlastrack

interface OnLocation {
    fun onLocationAccept(lat: Double, lng: Double)
    fun onLocationAccept(cityId: String?)
}