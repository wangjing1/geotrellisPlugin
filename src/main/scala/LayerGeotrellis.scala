package geotrellis

import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.util
import java.util.Map

import com.supermap.data.{GeoPicture, GeoRectangle, Geometry, PrjCoordSys, Rectangle2D, WorkspaceVersion}
import com.supermap.mapping.Layer
import com.supermap.mapping.LayerExtensionPlugin
import com.supermap.mapping.LayerExtensionBase
import com.supermap.mapping.MapPainter

import scala.collection.JavaConverters._
import geotrellis.raster._
import geotrellis.spark._
import geotrellis.spark.io._
import geotrellis.spark.io.hadoop._
import geotrellis.vector.Extent
import javax.imageio.ImageIO

import scala.concurrent._
import org.apache.hadoop.fs.Path
import org.apache.hadoop.conf.Configuration

class LayerGeotrellis extends LayerExtensionPlugin {
  private var m_params:java.util.Map[String, AnyRef] = null

  class LayerUserDraw(val handle: Long) extends LayerExtensionBase(handle) {
    setExtensionType(1000)
    private var userXML = ""
    private var m_params:java.util.Map[String, AnyRef] = null

    override def OnDraw(painter: MapPainter): Unit = {

      m_params = this.getExtensionUserInfo
      val paramPathString = m_params.get("url:String").asInstanceOf[String]//"hdfs://127.0.0.1:9000/geotrellis/catalogWebM/"
      val paramDataName =   m_params.get("dataName:String").asInstanceOf[String]
      val mapRect = painter.getMapDrawingBounds
      val prj:PrjCoordSys = painter.getMapDrawingPrj
      val mapepsgCode = prj.toEPSGCode
      //��ͼ��ʾ��Χ�ĵ�ͼ���
      //painter.getGraphics.setColor(Color.RED)
      //println(painter.getImageSize)
      //painter.getGraphics.drawRoundRect(50, 50, 200, 200, 50, 50)

      val mapheight = mapRect.getHeight
      val mapwidth = mapRect.getWidth

      //�����ʱ��û�ṩpainter.getbufferdimagesize�ӿ�
      val mapCols = 2000
      val mapRows = 2000

      //�����ͼ������
      val mapheightScale = mapCols/mapheight
      val mapwidtScale = mapCols/mapheight

      val configuration = new Configuration()
      // Create a reader that will read in the indexed tiles we produced in Ingest.

      val hadoopValueReader
      = HadoopValueReader(new Path(paramPathString), configuration)

      def reader(layerId: LayerId)
      = hadoopValueReader.reader[SpatialKey, MultibandTile](layerId)


      //��¼Ŀ��tile��ʾ������
      var targetTileScale = -1.0
      var targetZoom = -1
      var maxZoom = -1
      //ͨ��geotrellis��zoom�ȼ���Ӧ��ͼ�����߲㼶
      val attributeStore = hadoopValueReader.attributeStore
      var allLayers = attributeStore.layersWithZoomLevels
      allLayers.foreach(layerAndZooms => {
        var name = layerAndZooms._1
        val zooms = layerAndZooms._2.sortBy[Int](v => v)
        var minZoom = -1
        maxZoom = zooms.last
        var epsgcode = 0
        var layerAttribute:TileLayerMetadata[SpatialKey] = null

        for (z <- zooms if targetZoom == -1) {
          try {

            layerAttribute = attributeStore.readMetadata[TileLayerMetadata[SpatialKey]](LayerId(name, z))
            //��ǰ��Ƭ�����ؿ��
            val layertilecols = layerAttribute.tileLayout.tileCols
            val layertilerows = layerAttribute.tileLayout.tileRows

            val layertilegridBounds = layerAttribute.gridBounds
            layertilegridBounds.colMin
            val tilezoomExtent = layerAttribute.keyToExtent(layertilegridBounds.colMin,layertilegridBounds.colMax)
            val tileScale = layertilecols/tilezoomExtent.height
            epsgcode = layerAttribute.crs.epsgCode.get

            //�ҵ�epsg��ͬʱ�ı���������ʵ�tile�ȼ�

            if(epsgcode.toString == mapepsgCode.toString){

              if(mapheightScale < tileScale){
                targetTileScale = tileScale
                targetZoom = z

                print("tilescale zoom:")

                println(z)
                println(layerAttribute.tileLayout.isTiled)
                println(targetTileScale)
                println((name,epsgcode))
              }
            }
          } catch {
            case _: AttributeNotFoundError => println("the zoom " + z + " of layer " + name + "does not exist.")
          }
        }
      })

      if(targetZoom == -1) {
        targetZoom = maxZoom
        }

      val metaData: TileLayerMetadata[SpatialKey] = hadoopValueReader.
        attributeStore.readMetadata[TileLayerMetadata[SpatialKey]](LayerId(paramDataName, targetZoom))

      val mapminx = mapRect.getLeft
      val mapminy = mapRect.getBottom
      val mapmaxx = mapRect.getRight
      val mapmaxy = mapRect.getTop

      //��ͼ��ʾ��Χ�ĵ�ͼ���
      //painter.getGraphics.setColor(Color.RED)
      //println(painter.getImageSize)
      //painter.getGraphics.drawRoundRect(50, 50, 200, 200, 50, 50)


      //������Ƭ�����ؿ��
      val tilescols = metaData.layout.tileLayout.tileCols
      val tilesrows = metaData.layout.tileLayout.tileRows


      //��map��bounds����Ϊ��Ӧ��keyֵ
      val mapExtent:Extent = Extent(mapminx,mapminy,mapmaxx,mapmaxy)

      val mapGridextent = metaData.extentToBounds(mapExtent)


      val hadoopReader = hadoopValueReader.reader[SpatialKey, MultibandTile](LayerId(paramDataName, targetZoom))

      val keyBounds = metaData.bounds

      val tilesgridbounds = metaData.gridBounds
      val unionGridBounds = mapGridextent.intersection(tilesgridbounds)
      var showGridBounds:GridBounds = new GridBounds(0,0,-1,-1)
      for (bounds <- unionGridBounds){
        showGridBounds = bounds
      }


      if(showGridBounds.isEmpty){
        println("targetZoom:"+targetZoom)
        println("bounds is empty!")
        return
      }
      println("�Ա�����bounds")
      println("tilesgridbounds:"+tilesgridbounds.toString)
      println("mapgridbounds:"+mapGridextent)
      println("showGridBounds:"+showGridBounds)
      val raster = reader(LayerId("DEMLatLon", targetZoom))

      val ikey = showGridBounds.colMin
      val jkey = showGridBounds.rowMin
      for(ikey <- showGridBounds.colMin until showGridBounds.colMax+1 )
        for(jkey <- showGridBounds.rowMin until showGridBounds.rowMax+1 )
        {
          try {
            val tilem = hadoopReader.read(SpatialKey(ikey, jkey))
            val minmax = tilem.band(0).findMinMax

            println("minmax:")
            println(minmax)

            //renderpng�п��Խ�����ɫ�������,����ֻ�Ե������δ�����
            val png = tilem.band(0).renderPng()
            //println(jpg)
            //val path:String = "F:\\output\\ij".concat(ikey.toString).concat(jkey.toString).concat(".png")
            val inputstream = new ByteArrayInputStream(png.bytes)

            val bufferdimage = ImageIO.read(inputstream)

           val extentsBounds = metaData.keyToExtent(SpatialKey(ikey, jkey))
            val retangle2d:Rectangle2D = new Rectangle2D(extentsBounds.xmin,extentsBounds.ymin,extentsBounds.xmax,extentsBounds.ymax)
            val picture:GeoPicture = new GeoPicture(bufferdimage,retangle2d,0)
            //ͶӰת�������ڴ˴�
            painter.drawGeometry(picture)
          } catch {
            case ex: ValueNotFoundError =>{
              println("ValueNotFoundError")
            }
          }

        }
    }

    override def ToXML(version: WorkspaceVersion) = ""

    override def FromXML(xml: String): Unit = {
      userXML = xml
    }
  }

  private var m_layerUserDraw:LayerUserDraw = null

  override def getExtensionLayer: Layer = {
    m_layerUserDraw = new LayerUserDraw(0)
    m_layerUserDraw.setExtensionUserInfo(m_params)
    m_layerUserDraw
  }

  override def getPluginName = "LayerGeotrellis"

  override def setParams(params: java.util.Map[String, AnyRef]): Unit = {
    m_params = params
  }

  override def canProcess: Boolean = {
    if (m_params.get("url:String") != null) {
      val path = m_params.get("url:String").asInstanceOf[String]
      //to do  �жϵ�ǰpath�Ƿ����
       true
    }
    true
  }
}

