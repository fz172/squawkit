package dev.fanfly.wingslog

/** This source set is only compiled into the prod flavor. */
const val IS_DOGFOOD_BUILD = false

fun createDogfoodExtensions(): DogfoodFeatureExtensions = NoOpDogfoodExtensions
