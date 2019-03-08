package geotrellis.contrib.vlm

import geotrellis.contrib.vlm.geotiff._

import cats.data.NonEmptyList
import geotrellis.proj4.{LatLng, WebMercator}
import geotrellis.raster.{IntArrayTile, MultibandTile, Raster}
import geotrellis.vector.Extent
import org.scalatest._

class MosaicRasterSourceSpec extends FunSpec with Matchers {

  describe("union operations") {

    // With Extent(0, 0, 1, 1)
    val inputPath1 = Resource.path("img/geotiff-at-origin.tif")
    // With Extent(1, 0, 2, 1)
    val inputPath2 = Resource.path("img/geotiff-off-origin.tif")

    val gtRasterSource1 = GeoTiffRasterSource(inputPath1)
    val gtRasterSource2 = GeoTiffRasterSource(inputPath2)

    val mosaicRasterSource = MosaicRasterSource(
      NonEmptyList(gtRasterSource1, List(gtRasterSource2)), LatLng)

    it("should understand its bounds") {
      mosaicRasterSource.cols shouldBe 8
      mosaicRasterSource.rows shouldBe 4
    }

    it("should union extents of its sources") {
      mosaicRasterSource.rasterExtent shouldBe (
        gtRasterSource1.rasterExtent combine gtRasterSource2.rasterExtent
      )
    }

    it("should union extents with reprojection") {
      mosaicRasterSource.reproject(WebMercator).rasterExtent shouldBe (
        gtRasterSource1.reproject(WebMercator).rasterExtent combine
          gtRasterSource2.reproject(WebMercator).rasterExtent
      )
    }

    it("should return the whole tiles from the whole tiles' extents") {
      val extentRead1 = Extent(0, 0, 1, 1)
      val extentRead2 = Extent(1, 0, 2, 1)
      mosaicRasterSource.read(extentRead1, Seq(0)) shouldBe
        gtRasterSource1.read(gtRasterSource1.rasterExtent.extent, Seq(0))
      mosaicRasterSource.read(extentRead2, Seq(0)) shouldBe
        gtRasterSource2.read(gtRasterSource2.rasterExtent.extent, Seq(0))
    }

    it("should read an extent overlapping both tiles") {
      val extentRead = Extent(0, 0, 1.5, 1)
      val expectation = Raster(
        MultibandTile(IntArrayTile(Array(0, 1, 2, 3, 0, 1,
                                         4, 5, 6, 7, 4, 5,
                                         8, 9, 10, 11, 8, 9,
                                         12, 13, 14, 15, 12, 13),
                                   6, 4)),
        extentRead
      )
      val result = mosaicRasterSource.read(extentRead, Seq(0))
      result shouldBe Some(expectation)
    }

    it("should get the expected tile from a gridbounds-based read") {
      val expectation = Raster(
        MultibandTile(IntArrayTile(Array(0, 1, 2, 3, 0, 1, 2, 3,
                                         4, 5, 6, 7, 4, 5, 6, 7,
                                         8, 9, 10, 11, 8, 9, 10, 11,
                                         12, 13, 14, 15, 12, 13, 14, 15),
                                   8, 4)),
        mosaicRasterSource.rasterExtent.extent
      )
      val result = mosaicRasterSource.read(mosaicRasterSource.bounds, Seq(0))
      result shouldBe Some(expectation)
    }
  }
}
