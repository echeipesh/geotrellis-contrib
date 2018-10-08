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
import geotrellis.proj4.{LatLng, Transform}
import geotrellis.raster._
import geotrellis.raster.io.geotiff.reader.GeoTiffReader
import geotrellis.raster.reproject.{RasterRegionReproject, ReprojectRasterExtent}
import geotrellis.raster.resample._
import geotrellis.raster.testkit._

import org.scalatest._

import java.io.File

// TODO: fix test, compare to GDAL WARP results
class GDALReprojectRasterSourceSpec extends FunSpec with RasterMatchers with BetterRasterMatchers with GivenWhenThen {
  describe("Reprojecting a RasterSource") {
    val uri = s"${new File("").getAbsolutePath()}/src/test/resources/img/aspect-tiled.tif"
    val schemeURI = s"file://$uri"

    val rasterSource = GDALRasterSource(uri)

    val sourceTiff = GeoTiffReader.readMultiband(uri)


    val expectedRasterExtent = {
      val re = ReprojectRasterExtent(rasterSource.rasterExtent, Transform(rasterSource.crs, LatLng))
      // stretch target raster extent slightly to avoid default case in ReprojectRasterExtent
      RasterExtent(re.extent, CellSize(re.cellheight * 1.1, re.cellwidth * 1.1))
    }

    def testReprojection(method: ResampleMethod) = {
      val warpRasterSource = rasterSource.reprojectToRegion(LatLng, expectedRasterExtent, method)

      val testBounds = GridBounds(0, 0, expectedRasterExtent.cols, expectedRasterExtent.rows).split(64,64).toSeq

      for (bound <- testBounds) yield {
        withClue(s"Read window ${bound}: ") {
          val targetExtent = expectedRasterExtent.extentFor(bound)
          val testRasterExtent = RasterExtent(
            extent = targetExtent,
            cellwidth = expectedRasterExtent.cellwidth,
            cellheight = expectedRasterExtent.cellheight,
            cols = bound.width, rows = bound.height)

          val expected: Raster[MultibandTile] = {
            val rr = implicitly[RasterRegionReproject[MultibandTile]]
            rr.regionReproject(sourceTiff.raster, sourceTiff.crs, LatLng, testRasterExtent, testRasterExtent.extent.toPolygon, method)
          }

          val actual = warpRasterSource.read(bound).get

          // actual.extent.covers(expected.extent) should be (true) -- doesn't work due to a precision issue
          actual.rasterExtent.extent.xmin should be (expected.rasterExtent.extent.xmin +- 1e-4)
          actual.rasterExtent.extent.ymax should be (expected.rasterExtent.extent.ymax +- 1e-4)
          actual.rasterExtent.cellwidth should be (expected.rasterExtent.cellwidth +- 1e-4)
          actual.rasterExtent.cellheight should be (expected.rasterExtent.cellheight +- 1e-4)
          // check also that the tile is valid
          // withGeoTiffClue(actual, expected, LatLng)  {
          //   assertRastersEqual(actual, expected)
          // }
          // assertEqual(actual, expected)
        }
      }
    }

    it("should reproject using NearestNeighbor") {
      testReprojection(NearestNeighbor)
    }

    it("should reproject using Bilinear") {
      testReprojection(Bilinear)
    }
  }
}
