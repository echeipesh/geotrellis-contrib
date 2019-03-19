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

import cats.Semigroup
import cats.implicits._
import cats.data.NonEmptyList
import geotrellis.vector._
import geotrellis.raster._
import geotrellis.raster.merge.Implicits._
import geotrellis.raster.resample._
import geotrellis.raster.reproject.Reproject
import geotrellis.proj4.{CRS, WebMercator}
import geotrellis.raster.io.geotiff.{AutoHigherResolution, OverviewStrategy}
import geotrellis.raster.render._
import geotrellis.spark.tiling.LayoutDefinition
import geotrellis.util.GetComponent

/**
  * Single threaded instance of a reader for reading windows out of collections
  * of rasters
  *
  * @param sources The underlying [[RasterSource]]s that you'll use for data access
  * @param commonCrs The commonCrs to reproject all [[RasterSource]]s to anytime we need information about their data
  * Since MosaicRasterSources represent collections of [[RasterSource]]s, we don't know in advance
  * whether they'll have the same CRS. commonCrs allows specifying the CRS on read instead of
  * having to make sure at compile time that you're threading CRSes through everywhere correctly.
  */
case class MosaicRasterSource(sources: NonEmptyList[RasterSource], crs: CRS)
    extends RasterSource {

  import MosaicRasterSource._

  /**
    * Uri is required only for compatibility with RasterSource.
    *
    * It doesn't make sense to access "the" URI for a collection, so this throws an exception.
    */
  def uri: String = throw new NotImplementedError(
    """
      | MosaicRasterSources don't have a single uri. Perhaps you wanted the uri from
      | one of this MosaicRasterSource's sources?
    """.trim.stripMargin
  )

  val targetCellType = None

  /**
    * The bandCount of the first [[RasterSource]] in sources
    *
    * If this value is larger than the bandCount of later [[RasterSource]]s in sources,
    * reads of all bands will fail. It is a client's responsibility to construct
    * mosaics that can be read.
    */
  def bandCount: Int = sources.head.bandCount

  def cellType: CellType = sources.head.cellType

  def rasterExtent = {
    val extents = sources map { _.reproject(commonCrs).rasterExtent }
    extents.tail.foldLeft(extents.head) { _ combine _ }
  }

  /**
    * All available resolutions for all RasterSources in this MosaicRasterSource
    *
    * @see [[geotrellis.contrib.vlm.RasterSource.resolutions]]
    */
  def resolutions = {
    val resolutions = sources map { _.reproject(commonCrs).resolutions }
    resolutions.tail.foldLeft(resolutions.head)( _ ++ _ )
  }

  /** Create a new MosaicRasterSource with sources transformed according to the provided
    * crs, options, and strategy, and a new commonCrs
    *
    * @see [[geotrellis.contrib.vlm.RasterSource.reproject]]
    */
  def reproject(crs: CRS, reprojectOptions: Reproject.Options, strategy: OverviewStrategy)
      : RasterSource = MosaicRasterSource(
    sources map { _.reproject(crs, reprojectOptions, strategy) },
    crs
  )

  def read(extent: Extent, bands: Seq[Int]): Option[Raster[MultibandTile]] = {
    val rasters = sources map { _.reproject(commonCrs).read(extent, bands) }
    rasters.reduce
  }

  def read(bounds: GridBounds, bands: Seq[Int]): Option[Raster[MultibandTile]] = {
    val rasters = sources map { _.reproject(commonCrs).read(bounds, bands) }
    rasters.reduce
  }

  def resample(resampleGrid: ResampleGrid, method: ResampleMethod, strategy: OverviewStrategy)
      : RasterSource = MosaicRasterSource(
    sources map { _.reproject(commonCrs).resample(resampleGrid, method, strategy) },
    commonCrs
  )

  def convert(targetCellType: TargetCellType): RasterSource = {
    MosaicRasterSource(
      sources map { _.convert(targetCellType) },
      commonCrs
    )
  }
}

object MosaicRasterSource {
  // Orphan instance for semigroups for rasters, so we can combine
  // Option[Raster[_]]s later
  implicit val rasterSemigroup: Semigroup[Raster[MultibandTile]] =
    new Semigroup[Raster[MultibandTile]] {
      def combine(l: Raster[MultibandTile], r: Raster[MultibandTile]) = {
        val targetRE = RasterExtent(
          l.rasterExtent.extent combine r.rasterExtent.extent,
          List(l.rasterExtent.cellSize, r.rasterExtent.cellSize).minBy(_.resolution))
        val result = l.resample(targetRE) merge r.resample(targetRE)
        result
      }
    }

  @SuppressWarnings(Array("TraversableHead", "TraversableTail"))
  def unsafeFromList(sources: List[RasterSource], crs: CRS = WebMercator) =
    MosaicRasterSource(NonEmptyList(sources.head, sources.tail), crs)
}
