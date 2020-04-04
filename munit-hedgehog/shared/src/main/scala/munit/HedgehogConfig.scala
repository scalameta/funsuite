package munit

import hedgehog.core.PropertyConfig

case class HedgehogConfig(config: PropertyConfig) extends Tag("HedgehogConfig")