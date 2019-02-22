import java.awt.Dimension
import java.util

import com.supermap.data.{Point2D, PrjCoordSys, Rectangle2D, Workspace, WorkspaceConnectionInfo}
import com.supermap.mapping.Map


object testOutputMap {

  def main(args: Array[String]): Unit = {
      val m_workspace = new Workspace

      val m_workspaceconnectionInfo = new WorkspaceConnectionInfo("F:\\Worldcrash\\World.smwu") //("D:/pdf/????????/China/China400.smwu");//("D:/pdf/PDF????/???????????_??????·Ú????/SymbolicMapping/???????.smwu");//("D:/pdf/????????/China/China400.smwu");//("D://pdf//????????//China//China400.smwu");//("D:\\pdf\\PDF????\\???????????_??????·Ú????\\????\\sichuan.smwu");//("D:\\pdf\\PDF????\\???????????_??????·Ú????\\ReliefMap\\ChongQing\\ChongQing.smwu");//("D:\\????\\421181009_?????_20091126151736\\421181009_New.smwu");
      m_workspace.open(m_workspaceconnectionInfo)

      val map = new Map(m_workspace)
      map.open("new4")
      map.setVisibleScalesEnabled(false)

      val params: util.Map[String, AnyRef] = new util.HashMap[String, AnyRef]()
      params.put("url:String", "file:///D:\\GIS\\geogeo\\data\\catalog\\catalog")
      params.put("dataName:String", "DEMLatLon")
      params.put("pluginName:String", "LayerGeotrellis")
      map.getLayers.add(params, true)
      val pnt = new Point2D(75.49803977783205,40.51757362304687)
      map.setCenter(pnt)
      map.setScale(1/150000.0)
      val dim = new Dimension(2000,2000)
      map.setImageSize(dim)

    map.outputMapToBMP("f:\\png.bmp")
    print("done")

  }

}
