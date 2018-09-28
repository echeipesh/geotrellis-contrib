/*
 * Copyright 2018 Azavea
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package geotrellis.contrib.vlm

import geotrellis.contrib.vlm.gdal._
import geotrellis.raster._
import geotrellis.raster.reproject.Reproject

import geotrellis.proj4._
import geotrellis.spark._
import geotrellis.spark.io.hadoop._
import geotrellis.spark.tiling._
import geotrellis.spark.testkit._
import geotrellis.raster.testkit.RasterMatchers

import org.scalatest._

import java.io.File

class LayoutTileSourceSpec extends FunSpec with RasterMatchers with BetterRasterMatchers {
  val testFile = Resource.uri("img/aspect-tiled.tif")

  val targetCRS = CRS.fromEpsgCode(3857)
  val scheme = ZoomedLayoutScheme(targetCRS)
  val layout = scheme.levelForZoom(13).layout

  val rasterSource = new GeoTiffRasterSource(testFile.toString)
      .reprojectToGrid(targetCRS, layout)
  val source = new LayoutTileSource(rasterSource, layout)

  it("should read all the keys") {
    val keys = source.layout
      .mapTransform
      .extentToBounds(rasterSource.extent)
      .coordsIter
      .map { case (col, row) => SpatialKey(col, row) }
      .foreach{ key =>
        withClue(s"$key:") {
          val tile = source.read(key).get
          tile should have (
            dimensions (layout.tileCols, layout.tileRows),
            cellType (rasterSource.cellType)     
          )
        }
      }
  }
}
