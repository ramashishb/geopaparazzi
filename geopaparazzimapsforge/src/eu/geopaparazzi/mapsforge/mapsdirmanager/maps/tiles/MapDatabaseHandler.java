/*
 * Geopaparazzi - Digital field mapping on Android based devices
 * Copyright (C) 2010  HydroloGIS (www.hydrologis.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package eu.geopaparazzi.mapsforge.mapsdirmanager.maps.tiles;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import org.mapsforge.map.reader.MapDatabase;
import org.mapsforge.map.reader.header.FileOpenResult;
import org.mapsforge.map.reader.header.MapFileHeader;
import org.mapsforge.map.reader.header.MapFileInfo;
import org.mapsforge.core.model.GeoPoint;
import org.mapsforge.core.model.Tile;

import jsqlite.Exception;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Paint;
import eu.geopaparazzi.library.database.GPLog;
import eu.geopaparazzi.spatialite.database.spatial.core.MbtilesDatabaseHandler;

/**
 * An utility class to handle an map database.
 *
 * author Andrea Antonello (www.hydrologis.com)
 * adapted to work with map databases [mapsforge] Mark Johnson (www.mj10777.de)
 */
public class MapDatabaseHandler {
    public final static String TABLE_METADATA = "metadata";
    public final static String COL_METADATA_NAME = "name";
    public final static String COL_METADATA_VALUE = "value";
    private List<MapTable> mapTableList;
    // private int i_force_unique = 0;
    private FileOpenResult fileOpenResult;
    private MapDatabase map_Database = null;
    private MapFileInfo map_FileInfo=null;
    private File file_map; // all DatabaseHandler/Table classes should use these names
    private String s_map_file; // [with path] all DatabaseHandler/Table classes should use these names
    private String s_name_file; // [without path] all DatabaseHandler/Table classes should use these names
    private String s_name; // all DatabaseHandler/Table classes should use these names
    private String s_description; // all DatabaseHandler/Table classes should use these names
    private String s_map_type; // all DatabaseHandler/Table classes should use these names
    private int minZoom;
    private int maxZoom;
    private double centerX; // wsg84
    private double centerY; // wsg84
    private double bounds_west; // wsg84
    private double bounds_east; // wsg84
    private double bounds_north; // wsg84
    private double bounds_south; // wsg84
    private int defaultZoom;
    private String s_mbtiles_file; // mbtiles specific
    private File file_mbtiles = null; // mbtiles specific
    private String s_format; // mbtiles specific
    private String s_tile_row_type = "tms"; // mbtiles specific
    private int i_force_unique = 0;
    private int i_force_bounds = 1; // after each insert, update bounds and min/max zoom levels
    private MbtilesDatabaseHandler mbtiles_db = null;
    private HashMap<String, String> mbtiles_metadata = null;
    // we need a file_name and a short-name
    // -----------------------------------------------
    public MapDatabaseHandler( String dbPath ) {
        try {
           this.file_map=new File(dbPath);
           if (!file_map.exists()) {
                throw new RuntimeException();
           }
           map_Database = new MapDatabase();
           this.fileOpenResult = map_Database.openFile(this.file_map);
           if (fileOpenResult.isSuccess())
           {
            s_map_file=file_map.getAbsolutePath();
           }
           map_FileInfo = map_Database.getMapFileInfo();
           s_name=map_FileInfo.comment;
           s_name_file=file_map.getName();
           if ((s_name == null) || (s_name.length() == 0))
           {
            s_name=this.file_map.getName().substring(0,this.file_map.getName().lastIndexOf("."));
           }
           bounds_west = (double) (map_FileInfo.boundingBox.getMinLongitude());
           bounds_south = (double) (map_FileInfo.boundingBox.getMinLatitude());
           bounds_east = (double) (map_FileInfo.boundingBox.getMaxLongitude());
           bounds_north = (double) (map_FileInfo.boundingBox.getMaxLatitude());
           GeoPoint startPosition=map_FileInfo.startPosition;
           // long_description[california bounds[-125.8935,32.48171,-114.1291,42.01618] center[-120.0113,37.248945,14][-121.4944,38.58157]]
           if (startPosition == null)
           { // true center of map
            centerX = map_FileInfo.boundingBox.getCenterPoint().getLongitude();
            centerY = map_FileInfo.boundingBox.getCenterPoint().getLatitude();
           }
           else
           { // user-defined center of map
            centerY = startPosition.getLatitude();
            centerX = startPosition.getLongitude();
           }
           Byte startZoomLevel=map_FileInfo.startZoomLevel;
           // Byte startZoomLevel = getMinZoomlevel(map_Database);
           if (startZoomLevel != null) {
            defaultZoom = startZoomLevel;
           }
           else
           {
            defaultZoom = 14;
           }
           maxZoom = 22;
           setDescription(s_name);
        } catch (java.lang.Exception e) {
            GPLog.androidLog(4,"MapDatabaseHandler[" + file_map.getAbsolutePath() + "]", e);
        }
        // GPLog.androidLog(-1,"MapDatabaseHandler[" + file_map.getAbsolutePath() + "] name["+s_name+"] s_description["+s_description+"]");
    }
    /**
     * Workaround to get the min zoom from a map file.
     *
     * <p>This is done through reflection since the map_FileInfo.startZoomLevel value
     * is different from the minZoomLevel of the file , but the minZoomLevel is
     * not accessible.
     * <p>Also the  startZoomLevel limits the level to 14, which is why this
     * workaround was done.
     * 20131215: mj10777 this may possible be no longer needed, since startZoomLevel is public
     * - berlin brandenburg show 8, us-maps 14
     * -- this routine fails [may possible be used only after the file has been loaded]
     * http://trac.openlayers.org/wiki/SettingZoomLevels
     * @param mapDatabase the {@link MapDatabase} to guess the min zoomlevel from.
     * @return the min zoomlevel or a default value 1.
     */
    private Byte getMinZoomlevel( MapDatabase mapDatabase ) {
        try {
            Field f1 = mapDatabase.getClass().getDeclaredField("mapFileHeader");
            f1.setAccessible(true);
            MapFileHeader mapFileHeader = (MapFileHeader) f1.get(mapDatabase);
            Field f2 = mapFileHeader.getClass().getDeclaredField("zoomLevelMinimum");
            f2.setAccessible(true);
            Object object = f2.get(mapFileHeader);
            Byte minZoomlevel = (Byte) object;
            return minZoomlevel;
        } catch (java.lang.Exception e) {
            // ignore and return default
            return 1;
        }

    }
    // -----------------------------------------------
    /**
      * Return long name of map/file
      *
      * <p>default: file name with path and extention
      * <p>mbtiles : will be a '.mbtiles' sqlite-file-name
      * <p>map : will be a mapforge '.map' file-name
      *
      * @return file_map.getAbsolutePath();
      */
    public String getFileNamePath() {
        return this.s_map_file; // file_map.getAbsolutePath();
    }
    // -----------------------------------------------
    /**
      * Return short name of map/file
      *
      * <p>default: file name without path but with extention
      *
      * @return file_map.getAbsolutePath();
      */
    public String getFileName() {
        return this.s_name_file; // file_map.getName();
    }
    // -----------------------------------------------
    /**
      * Return short name of map/file
      *
      * <p>default: file name without path and extention
      * <p>mbtiles : metadata 'name'
      * <p>map : will be value of 'comment', if not null
      *
      * @return s_name as short name of map/file
      */
    public String getName() {
        if ((s_name == null) || (s_name.length() == 0))
        {
         s_name=this.file_map.getName().substring(0,this.file_map.getName().lastIndexOf("."));
        }
        return this.s_name; // comment or file-name without path and extention
    }
        // -----------------------------------------------
    /**
      * Return String of bounds [wms-format]
      *
      * <p>x_min,y_min,x_max,y_max
      *
      * @return bounds formatted using wms format
      */
    public String getBounds_toString() {
        return bounds_west+","+bounds_south+","+bounds_east+","+bounds_north;
    }
    // -----------------------------------------------
    /**
      * Return String of Map-Center with default Zoom
      *
      * <p>x_position,y_position,default_zoom
      *
      * @return center formatted using mbtiles format
      */
    public String getCenter_toString() {
        return centerX+","+centerY+","+defaultZoom;
    }
    // -----------------------------------------------
    /**
      * Return long description of map/file
      *
      * <p>default: s_name with bounds and center
      * <p>mbtiles : metadata description'
      * <p>map : will be value of 'comment', if not null
      *
      * @return s_description long description of map/file
      */
    public String getDescription() {
        if ((this.s_description == null) || (this.s_description.length() == 0) || (this.s_description.equals(this.s_name)))
         setDescription(getName()); // will set default values with bounds and center if it is the same as 's_name' or empty
        return this.s_description; // long comment
    }
     // -----------------------------------------------
    /**
      * Set long description of map/file
      *
      * <p>default: s_name with bounds and center
      * <p>mbtiles : metadata description'
      * <p>map : will be value of 'comment', if not null
      *
      * @return s_description long description of map/file
      */
    public void setDescription(String s_description) {
        if ((s_description == null) || (s_description.length() == 0) || (s_description.equals(this.s_name)))
        {
         this.s_description = getName()+" bounds["+getBounds_toString()+"] center["+getCenter_toString()+"]";
        }
        else
         this.s_description = s_description;
    }
    // -----------------------------------------------
    /**
      * Return map-file as 'File'
      *
      * <p>if the class does not fail, this file exists
      * <p>mbtiles : will be a '.mbtiles' sqlite-file
      * <p>map : will be a mapforge '.map' file
      *
      * @return file_map as File
      */
    public File getFile() {
        return this.file_map;
    }
    // -----------------------------------------------
    /**
      * Return Min Zoom
      *
      * <p>default :  0
      * <p>mbtiles : taken from value of metadata 'minzoom'
      * <p>map : value is given in 'StartZoomLevel'
      *
      * @return integer minzoom
      */
    public int getMinZoom() {
        return minZoom;
    }
    // -----------------------------------------------
    /**
      * Return Max Zoom
      *
      * <p>default :  22
      * <p>mbtiles : taken from value of metadata 'maxzoom'
      * <p>map : value not defined, seems to calculate bitmap from vector data [18]
      *
      * @return integer maxzoom
      */
    public int getMaxZoom() {
        return maxZoom;
    }
    // -----------------------------------------------
    /**
      * Return Min/Max Zoom as string
      *
      * <p>default :  1-22
      * <p>mbtiles : taken from value of metadata 'min/maxzoom'
      *
      * @return String min/maxzoom
      */
    public String getZoom_Levels() {
        return getMinZoom()+"-"+getMaxZoom();
    }
    // -----------------------------------------------
    /**
      * Return West X Value [Longitude]
      *
      * <p>default :  -180.0 [if not otherwise set]
      * <p>mbtiles : taken from 1st value of metadata 'bounds'
      *
      * @return double of West X Value [Longitude]
      */
    public double getMinLongitude() {
        return bounds_west;
    }
    // -----------------------------------------------
    /**
      * Return South Y Value [Latitude]
      *
      * <p>default :  -85.05113 [if not otherwise set]
      * <p>mbtiles : taken from 2nd value of metadata 'bounds'
      *
      * @return double of South Y Value [Latitude]
      */
    public double getMinLatitude() {
        return bounds_south;
    }
    // -----------------------------------------------
    /**
      * Return East X Value [Longitude]
      *
      * <p>default :  180.0 [if not otherwise set]
      * <p>mbtiles : taken from 3th value of metadata 'bounds'
      *
      * @return double of East X Value [Longitude]
      */
    public double getMaxLongitude() {
        return bounds_east;
    }
    // -----------------------------------------------
    /**
      * Return North Y Value [Latitude]
      *
      * <p>default :  85.05113 [if not otherwise set]
      * <p>mbtiles : taken from 4th value of metadata 'bounds'
      *
      * @return double of North Y Value [Latitude]
      */
    public double getMaxLatitude() {
        return bounds_north;
    }
    // -----------------------------------------------
    /**
      * Return Center X Value [Longitude]
      *
      * <p>default : center of bounds
      * <p>mbtiles : taken from 1st value of metadata 'center'
      *
      * @return double of X Value [Longitude]
      */
    public double getCenterX() {
        return centerX;
    }
    // -----------------------------------------------
    /**
      * Return Center Y Value [Latitude]
      *
      * <p>default : center of bounds
      * <p>mbtiles : taken from 2nd value of metadata 'center'
      *
      * @return double of Y Value [Latitude]
      */
    public double getCenterY() {
        return centerY;
    }
    // -----------------------------------------------
    /**
      * Retrieve Zoom level
      *
      * <p>default : minZoom
      * <p>mbtiles : taken from 3rd value of metadata 'center'
      *
     * @return defaultZoom
      */
    public int getDefaultZoom() {
        return defaultZoom;
    }
    // -----------------------------------------------
    /**
      * Set default Zoom level
      *
      * <p>default : minZoom
      * <p>mbtiles : taken from 3rd value of metadata 'center'
      *
      * @param i_zoom desired Zoom level
      */
    public void setDefaultZoom( int i_zoom ) {
        defaultZoom = i_zoom;
    }
    public List<MapTable> getTables( boolean forceRead ) throws Exception {
        if (mapTableList == null || forceRead) {
            mapTableList = new ArrayList<MapTable>();
            double[] d_bounds = {bounds_west, bounds_south, bounds_east, bounds_north};
            // String tableName = metadata.name;
            String columnName = null;
            MapTable table = new MapTable(s_map_file, s_name, "3857", minZoom,
                    maxZoom, centerX, centerY, "?,?,?", d_bounds);
            table.setDefaultZoom(defaultZoom);
            // for mbtiles the desired center can be set by the
            // database developer and may be different than the
            // true center/zoom
            mapTableList.add(table);
        }
        return mapTableList;
    }

    // -----------------------------------------------
    /**
      * Function to retrieve Tile byte[] from the mbtiles Database [for 'SpatialiteDatabaseHandler']
      *
      * <p>i_y_osm must be in is Open-Street-Map 'Slippy Map'
      * notation [will be converted to 'tms' notation if needed]
      *
      * @param query Format 'z,x,y_osm'
      * @return byte[] of the tile or null if no tile matched the given parameters
      */
    public byte[] getRasterTile( String query ) {
        String[] split = query.split(",");
        if (split.length != 3) {
            return null;
        }
        int i_z = 0;
        int i_x = 0;
        int i_y_osm = 0;
        try {
            i_z = Integer.parseInt(split[0]);
            i_x = Integer.parseInt(split[1]);
            i_y_osm = Integer.parseInt(split[2]);
        } catch (NumberFormatException e) {
            return null;
        }
        Tile tile = new Tile((long)i_x,(long)i_y_osm,(byte)i_z);
        // mbtiles_db
        byte[] tileAsBytes = null; // db_mbtiles.getTileAsBytes(i_x, i_y_osm, i_z);
        return tileAsBytes;
    }
    // -----------------------------------------------
    /**
      * Function to retrieve Tile Bitmap from the mbtiles Database [for 'CustomTileDownloader']
      *
      * <p>i_y_osm must be in is Open-Street-Map 'Slippy Map' notation [will be converted to 'tms' notation if needed]
      *
      * @param i_x the value for tile_column field in the map,tiles Tables and part of the tile_id when image is not blank
      * @param i_y_osm the value for tile_row field in the map,tiles Tables and part of the tile_id when image is not blank
      * @param i_z the value for zoom_level field in the map,tiles Tables and part of the tile_id when image is not blank
      * @param i_pixel_size the value for zoom_level field in the map,tiles Tables and part of the tile_id when image is not blank
      * @param tile_bitmap retrieve the Bitmap as done in 'CustomTileDownloader'
      * @return Bitmap of the tile or null if no tile matched the given parameters
      */
    public boolean getBitmapTile( int i_x, int i_y_osm, int i_z, int i_pixel_size, Bitmap tile_bitmap ) {
        boolean b_rc = true;
        Tile tile = new Tile((long)i_x,(long)i_y_osm,(byte)i_z);
        /*
        if (db_mbtiles.getmbtiles() == null) { // in case .'open' was forgotten
            db_mbtiles.open(true, ""); // "" : default value will be used '1.1'
        }
        int[] pixels = new int[i_pixel_size * i_pixel_size];
        byte[] rasterBytes = db_mbtiles.getTileAsBytes(i_x, i_y_osm, i_z);
        if (rasterBytes == null) {
            b_rc = false;
            return b_rc;
        }
        Bitmap decodedBitmap = null;
        decodedBitmap = BitmapFactory.decodeByteArray(rasterBytes, 0, rasterBytes.length);
        // check if the input stream could be decoded into a bitmap
        if (decodedBitmap != null) { // copy all pixels from the decoded bitmap to the color array
            decodedBitmap.getPixels(pixels, 0, i_pixel_size, 0, 0, i_pixel_size, i_pixel_size);
            decodedBitmap.recycle();
        } else {
            b_rc = false;
            return b_rc;
        }
        // copy all pixels from the color array to the tile bitmap
        tile_bitmap.setPixels(pixels, 0, i_pixel_size, 0, 0, i_pixel_size, i_pixel_size);
        */
        return b_rc;
    }

    // -----------------------------------------------
    /**
      * Function to insert a new Tile Bitmap to the mbtiles Database
      *
      * <ul>
      *  <li>i_y_osm must be in is Open-Street-Map 'Slippy Map' notation [will
      *      be converted to 'tms' notation if needed]</li>
      *  <li>checking will be done to determine if the Bitmap is blank [i.e.
      *      all pixels have the same RGB]</li>
      * </ul>
      *
      * @param i_x the value for tile_column field in the map,tiles Tables and part of the tile_id when image is not blank
      * @param i_y_osm the value for tile_row field in the map,tiles Tables and part of the tile_id when image is not blank
      * @param i_z the value for zoom_level field in the map,tiles Tables and part of the tile_id when image is not blank
      * @param tile_bitmap the Bitmap to extract image-data extracted from. [Will be converted to JPG or PNG depending on metdata setting]
      * @return 0: correct, otherwise error
      */
    public int insertBitmapTile( int i_x, int i_y_osm, int i_z, Bitmap tile_bitmap, int i_force_unique)
            throws IOException { // i_rc= correct, otherwise error
        int i_rc = 0;
        try { // i_rc=0: inserted [if needed bounds min/max zoom have been updated]
            i_rc = mbtiles_db.insertBitmapTile(i_x, i_y_osm, i_z, tile_bitmap, i_force_unique);
        } catch (IOException e) {
            i_rc = 1;
            // e.printStackTrace();
        }
        return i_rc;
    }

    // -----------------------------------------------
    /**
      * Close map Database
      * @return void
      */
    public void close() throws Exception {
        if (map_Database != null) {
         map_Database.closeFile();
        }
    }
    // -----------------------------------------------
    /**
      * Update mbtiles Bounds / Zoom (min/max) levels
      * @return void
      */
    public void update_bounds() {
        if (mbtiles_db != null) {
            mbtiles_db.update_bounds(0);
        }
    }
}
