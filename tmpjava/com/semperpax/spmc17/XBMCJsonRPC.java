package com.semperpax.spmc17;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONObject;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.BaseColumns;
import android.util.Log;

import com.semperpax.spmc17.channels.model.Media;
import com.semperpax.spmc17.channels.model.Subscription;

public class XBMCJsonRPC
{
  public final static String APP_NAME = "SPMC Search";
  public final static String COLUMN_FULL_PATH = "COLUMN_FULL_PATH";
  public final static String COLUMN_BASE_PATH = "COLUMN_BASE_PATH";
  public final static String COLUMN_FILENAME = "COLUMN_FILENAME";
  public final static String COLUMN_TITLE = "COLUMN_TITLE";
  public final static String COLUMN_TAGLINE = "COLUMN_TAGLINE";
  public final static String COLUMN_THUMB = "COLUMN_THUMB";
  public final static String COLUMN_FANART = "COLUMN_FANART";
  public final static String COLUMN_ID = "COLUMN_ID";
  public final static String COLUMN_VIEW_PROGRESS = "COLUMN_VIEW_PROGRESS";
  public final static String COLUMN_RECOMMENDATION_REASON = "COLUMN_RECOMMENDATION_REASON";

  public final static String REQ_ID_MOVIES = "1";
  public final static String REQ_ID_SHOWS = "2";
  public final static String REQ_ID_ALBUMS = "3";
  public final static String REQ_ID_ARTISTS = "4";
  public final static String REQ_ID_MOVIES_ACTOR = "5";
  public final static String REQ_ID_SHOWS_ACTOR = "6";

  private static String TAG = "SPMCjson";

  private String m_jsonURL = "http://localhost:8080";
  private java.util.HashSet<Integer> mRecomendationIds = new java.util.HashSet<Integer>();

  private int MAX_RECOMMENDATIONS = 3;

  // {"jsonrpc": "2.0", "method": "VideoLibrary.GetMovies", "params": { "filter": {"field": "playcount", "operator": "is", "value": "0"}, "limits": { "start" : 0, "end": 3}, "properties" : ["imdbnumber", "title", "tagline", "thumbnail", "fanart"], "sort": { "order": "descending", "method": "dateadded", "ignorearticle": true } }, "id": "1"}
  private String RECOMMENDATION_MOVIES_JSON =
                  "{\"jsonrpc\": \"2.0\", \"method\": \"VideoLibrary.GetMovies\", "
                  + "\"params\": { \"filter\": {\"field\": \"playcount\", \"operator\": \"is\", \"value\": \"0\"}, "
                  + "\"limits\": { \"start\" : 0, \"end\": 10}, "
                  + "\"properties\" : [\"imdbnumber\", \"title\", \"tagline\", \"thumbnail\", \"fanart\"], "
                  + "\"sort\": { \"order\": \"descending\", \"method\": \"random\", \"ignorearticle\": true } }, "
                  + "\"id\": \"1\"}";

  private String RECOMMENDATIONS_SHOWS_JSON =
                 "{\"jsonrpc\":\"2.0\",\"method\":\"VideoLibrary.GetTVShows\",\"params\":{\"filter\":{\"and\":[{\"field\":\"playcount\",\"operator\":\"is\",\"value\":\"0\"},{\"field\":\"plot\",\"operator\":\"isnot\",\"value\":\"\"}]},\"limits\":{\"start\":0,\"end\":10},\"properties\":[\"imdbnumber\",\"title\",\"plot\",\"thumbnail\",\"fanart\"],\"sort\":{\"order\":\"descending\",\"method\":\"lastplayed\",\"ignorearticle\":true}},\"id\":\"1\"}";

  private String RECOMMENDATIONS_ALBUMS_JSON =
                 "{\"jsonrpc\": \"2.0\", \"method\": \"AudioLibrary.GetAlbums\", \"params\": { \"limits\": { \"start\" : 0, \"end\": 3}, \"properties\" : [\"title\", \"displayartist\", \"thumbnail\", \"fanart\"], \"sort\": { \"order\": \"descending\", \"method\": \"random\", \"ignorearticle\": true } }, \"id\": \"1\"}";

  private String SEARCH_MOVIES_JSON =
                  "{\"jsonrpc\": \"2.0\", \"method\": \"VideoLibrary.GetMovies\", "
                  + "\"params\": { \"filter\": {%s}, "
                  + "\"limits\": { \"start\" : 0, \"end\": 10}, "
                  + "\"properties\" : [\"imdbnumber\", \"title\", \"tagline\", \"thumbnail\", \"fanart\", \"year\", \"runtime\"], "
                  + "\"sort\": { \"order\": \"ascending\", \"method\": \"title\", \"ignorearticle\": true } }, "
                  + "\"id\": \"%s\"}";

  private String SEARCH_SHOWS_JSON =
           "{\"jsonrpc\":\"2.0\",\"method\":\"VideoLibrary.GetTVShows\",\"params\":{\"filter\":{%s},\"limits\":{\"start\":0,\"end\":10},\"properties\":[\"imdbnumber\",\"title\",\"plot\",\"thumbnail\",\"fanart\",\"year\"],\"sort\":{\"order\":\"descending\",\"method\":\"lastplayed\",\"ignorearticle\":true}},\"id\":\"%s\"}";

  private String SEARCH_ALBUMS_JSON =
                 "{\"jsonrpc\": \"2.0\", \"method\": \"AudioLibrary.GetAlbums\", \"params\": {\"filter\":{%s},\"limits\": { \"start\" : 0, \"end\": 10}, \"properties\" : [\"title\", \"displayartist\", \"thumbnail\", \"fanart\"], \"sort\": { \"order\": \"descending\", \"method\": \"dateadded\", \"ignorearticle\": true } }, \"id\": \"%s\"}";

  private String SEARCH_ARTISTS_JSON =
                 "{\"jsonrpc\": \"2.0\", \"method\": \"AudioLibrary.GetArtists\", \"params\": {\"filter\":{%s},\"limits\": { \"start\" : 0, \"end\": 10}, \"properties\" : [\"description\", \"thumbnail\", \"fanart\"], \"sort\": { \"order\": \"descending\", \"method\": \"dateadded\", \"ignorearticle\": true } }, \"id\": \"%s\"}";

  private String RETRIEVE_VIDEO_SMARTPLAYLISTS =
          "{ \"jsonrpc\": \"2.0\", \"method\": \"Files.GetDirectory\", \"params\": { \"directory\" : \"special://profile/playlists/video/\" }, \"id\": \"%s\" }";

  private String RETRIEVE_VIDEO_SMARTPLAYLIST_ITEMS =
          "{ \"jsonrpc\": \"2.0\", \"method\": \"Files.GetDirectory\", \"params\": { \"directory\" : \"%s\", \"limits\": { \"start\" : 0, \"end\": 10} }, \"id\": \"%s\" }";

  private String RETRIEVE_MOVIE_DETAILS =
          "{ \"jsonrpc\": \"2.0\", \"method\": \"VideoLibrary.GetMovieDetails\", \"params\": { \"movieid\" : %s, \"properties\" : [\"imdbnumber\", \"title\", \"tagline\", \"thumbnail\", \"fanart\", \"year\", \"runtime\"] }, \"id\": 1 }";

  private NotificationManager mNotificationManager;

  public XBMCJsonRPC()
  {
    String jsonPort = XBMCProperties.getStringProperty("xbmc.jsonPort", "8080");
    m_jsonURL = "http://localhost:" + jsonPort;
  }

  public String request_string(String jsonRequest)
  {
    try
    {
      //Log.d(TAG, "JSON in: " + jsonRequest);
      //Log.d(TAG, "JSON url: " + m_jsonURL);

      String returnStr = null;
      StringBuilder strbuilder = new StringBuilder();

      URL url = new URL(m_jsonURL + "/jsonrpc");
      HttpURLConnection connection = (HttpURLConnection) url.openConnection();
      connection.setDoOutput(true);
      connection.setDoInput(true);
      connection.setRequestProperty("Content-Type", "application/json");
      String auth = XBMCProperties.getJsonAuthorization();
      if (!auth.isEmpty())
        connection.setRequestProperty("Authorization", auth);
      connection.setRequestMethod("POST");

      OutputStreamWriter writer = new OutputStreamWriter(connection.getOutputStream());
      writer.write(jsonRequest);
      writer.close();

      try
      {
        int statusCode = connection.getResponseCode();
        if (statusCode == 200)
        {
          InputStream content = connection.getInputStream();
          BufferedReader reader = new BufferedReader(new InputStreamReader(content, "UTF-8"));
          String line;
          while ((line = reader.readLine()) != null)
          {
            strbuilder.append(line);
          }
          reader.close();
          connection.disconnect();

          // Log.d(TAG, "JSON out: " + strbuilder.toString());
          returnStr = strbuilder.toString();

        }
        else
        {
          Log.e(TAG, "Failed to read JSON");
        }
      }
      catch (Exception e)
      {
        e.printStackTrace();
      }
      finally
      {
        connection.disconnect();
      }
      return returnStr;
    }
    catch (ConnectException ce)
    {
      // Connection not available or died
      return null;
    }
    catch (Exception e)
    {
      Log.e(TAG, "Failed to read JSON");
      e.printStackTrace();
      return null;
    }
  }

  public JSONObject request_object(String jsonRequest)
  {
    try
    {
      String stringResp = request_string(jsonRequest);
      if (stringResp == null)
        return null;
      JSONObject resp = new JSONObject(stringResp);
      return resp;
    }
    catch (Exception e)
    {
      Log.e(TAG, "Failed to parse JSON");
      e.printStackTrace();
      return null;
    }
  }

  public JSONArray request_array(String jsonRequest)
  {
    try
    {
      String stringResp = request_string(jsonRequest);
      if (stringResp == null)
        return null;
      JSONArray resp = new JSONArray(stringResp);
      return resp;
    }
    catch (Exception e)
    {
      Log.e(TAG, "Failed to parse JSON");
      e.printStackTrace();
      return null;
    }
  }

  public Bitmap getBitmap(String src)
  {
    try
    {
      JSONObject req = request_object("{\"jsonrpc\": \"2.0\", \"method\": \"Files.PrepareDownload\", \"params\": { \"path\": \""
          + src + "\"}, \"id\": \"1\"}");
      if (req == null || req.isNull("result"))
        return null;

      JSONObject result = req.getJSONObject("result");
      String surl = result.getJSONObject("details").getString("path");

      URL url = new URL(m_jsonURL + "/" + surl);
      HttpURLConnection connection = (HttpURLConnection) url.openConnection();
      String auth = XBMCProperties.getJsonAuthorization();
      if (!auth.isEmpty())
        connection.setRequestProperty("Authorization", auth);
      connection.setDoInput(true);
      connection.connect();
      InputStream input = connection.getInputStream();
      Bitmap myBitmap = BitmapFactory.decodeStream(input);
      return myBitmap;
    } catch (Exception e)
    {
      e.printStackTrace();
      return null;
    }
  }

  public String getBitmapUrl(String src)
  {
    try
    {
      JSONObject req = request_object("{\"jsonrpc\": \"2.0\", \"method\": \"Files.PrepareDownload\", \"params\": { \"path\": \""
          + src + "\"}, \"id\": \"1\"}");
      if (req == null || req.isNull("result"))
        return null;

      JSONObject result = req.getJSONObject("result");
      String surl = result.getJSONObject("details").getString("path");

      return (m_jsonURL + "/" + surl);
    } catch (Exception e)
    {
      e.printStackTrace();
      return null;
    }
  }

  public Cursor search(String query)
  {
      String[] menuCols = new String[] {
              BaseColumns._ID,
              COLUMN_TITLE,
              COLUMN_TAGLINE,
              COLUMN_THUMB,
              COLUMN_FANART,
      };
      MatrixCursor mc = new MatrixCursor(menuCols);

      try
      {
        JSONObject req = request_object(String.format(SEARCH_MOVIES_JSON, /*"\"operator\": \"contains\", \"field\": \"title\", \"value\": \"" + query + "\"", limit));*/
        "\"or\": [" +
        "{\"operator\": \"contains\", \"field\": \"title\", \"value\": \"" + query + "\"}," +
        "{\"operator\": \"contains\", \"field\": \"originaltitle\", \"value\": \"" + query + "\"}," +
        "{\"operator\": \"contains\", \"field\": \"set\", \"value\": \"" + query + "\"}," +
        "{\"operator\": \"contains\", \"field\": \"actor\", \"value\": \"" + query + "\"}," +
        "{\"operator\": \"contains\", \"field\": \"director\", \"value\": \"" + query + "\"}]"));

        if (req == null || req.isNull("result"))
          return null;

        JSONObject results = req.getJSONObject("result");
        JSONArray movies = results.getJSONArray("movies");

        for (int i = 0; i < movies.length(); ++i)
        {
          JSONObject movie = movies.getJSONObject(i);
          mc.addRow(new Object[]{movie.getString("movieid"), movie.getString("title"), movie.getString("tagline"), movie.getString("thumbnail"), movie.getString("fanart")});
        }
      } catch (Exception e)
      {
        e.printStackTrace();
        return null;
      }

      try
      {
        JSONObject req = request_object(String.format(SEARCH_SHOWS_JSON, /*"\"operator\": \"contains\", \"field\": \"title\", \"value\": \"" + query + "\"", limit));*/
        "\"or\": [" +
        "{\"operator\": \"contains\", \"field\": \"title\", \"value\": \"" + query + "\"}," +
        "{\"operator\": \"contains\", \"field\": \"actor\", \"value\": \"" + query + "\"}," +
        "{\"operator\": \"contains\", \"field\": \"director\", \"value\": \"" + query + "\"}]"));

        if (req == null || req.isNull("result"))
          return null;

        JSONObject results = req.getJSONObject("result");
        JSONArray tvshows = results.getJSONArray("tvshows");

        for (int i = 0; i < tvshows.length(); ++i)
        {
          JSONObject tvshow = tvshows.getJSONObject(i);
          mc.addRow(new Object[]{tvshow.getString("movieid"), tvshow.getString("title"), tvshow.getString("plot"), tvshow.getString("thumbnail"), tvshow.getString("fanart")});
        }
      } catch (Exception e)
      {
        e.printStackTrace();
        return null;
      }

      return mc;
  }

  public Cursor getSuggestions(String query, int limit)
  {
    //Log.d(TAG, "query: " + query);

    int totCount = 0;
    String[] menuCols = new String[]
    {
        BaseColumns._ID,
        SearchManager.SUGGEST_COLUMN_TEXT_1,
        SearchManager.SUGGEST_COLUMN_TEXT_2,
        SearchManager.SUGGEST_COLUMN_ICON_1,
        SearchManager.SUGGEST_COLUMN_RESULT_CARD_IMAGE,
        SearchManager.SUGGEST_COLUMN_INTENT_ACTION,
        SearchManager.SUGGEST_COLUMN_INTENT_DATA,
        SearchManager.SUGGEST_COLUMN_VIDEO_WIDTH,
        SearchManager.SUGGEST_COLUMN_VIDEO_HEIGHT,
        SearchManager.SUGGEST_COLUMN_PRODUCTION_YEAR,
        SearchManager.SUGGEST_COLUMN_DURATION,
        SearchManager.SUGGEST_COLUMN_SHORTCUT_ID
    };
    MatrixCursor mc = new MatrixCursor(menuCols);

    String str_req = "[" +
      String.format(SEARCH_MOVIES_JSON,
        "\"or\": [" +
        "{\"operator\": \"contains\", \"field\": \"title\", \"value\": \"" + query + "\"}," +
        "{\"operator\": \"contains\", \"field\": \"originaltitle\", \"value\": \"" + query + "\"}," +
        "{\"operator\": \"contains\", \"field\": \"set\", \"value\": \"" + query + "\"}]", REQ_ID_MOVIES) +
      "," +
      String.format(SEARCH_SHOWS_JSON,
        "\"or\": [" +
        "{\"operator\": \"contains\", \"field\": \"title\", \"value\": \"" + query + "\"}," +
        "{\"operator\": \"contains\", \"field\": \"originaltitle\", \"value\": \"" + query + "\"}]", REQ_ID_SHOWS) +
    "," +
      String.format(SEARCH_ALBUMS_JSON,
        "\"or\": [" +
        "{\"operator\": \"contains\", \"field\": \"album\", \"value\": \"" + query + "\"}," +
        "{\"operator\": \"contains\", \"field\": \"label\", \"value\": \"" + query + "\"}]", REQ_ID_ALBUMS) +
    "," +
      String.format(SEARCH_ARTISTS_JSON,
        "\"operator\": \"contains\", \"field\": \"artist\", \"value\": \"" + query + "\"", REQ_ID_ARTISTS) +
    "," +
      String.format(SEARCH_MOVIES_JSON,
        "\"or\": [" +
         "{\"operator\": \"contains\", \"field\": \"actor\", \"value\": \"" + query + "\"}," +
        "{\"operator\": \"contains\", \"field\": \"director\", \"value\": \"" + query + "\"}]", REQ_ID_MOVIES_ACTOR) +
    "," +
      String.format(SEARCH_SHOWS_JSON,
        "\"or\": [" +
        "{\"operator\": \"contains\", \"field\": \"actor\", \"value\": \"" + query + "\"}," +
        "{\"operator\": \"contains\", \"field\": \"director\", \"value\": \"" + query + "\"}]", REQ_ID_SHOWS_ACTOR) +
    "]";

    JSONArray res_array = request_array(str_req);
    if (res_array == null)
      return null;

    int nb_movies = 0;
    int nb_shows = 0;
    for (int j = 0; j < res_array.length(); ++j)
    {
      String id;
      JSONObject resp;
      try
      {
        resp = res_array.getJSONObject(j);
        if (resp == null)
          continue;

        id = resp.getString("id");
      }
      catch (Exception e)
      {
        e.printStackTrace();
        continue;
      }

      if (id.equals(REQ_ID_MOVIES) || ((nb_movies + nb_shows) < 3 && id.equals(REQ_ID_MOVIES_ACTOR)))
      {
        searchmovies: try
        {
          if (resp.isNull("result"))
            break searchmovies;
          JSONObject results = resp.getJSONObject("result");
          if (results == null || results.isNull("movies"))
            break searchmovies;
          JSONArray movies = results.getJSONArray("movies");

          for (int i = 0; i < movies.length() && totCount < limit; ++i)
          {
            JSONObject movie = movies.getJSONObject(i);

            int rYear = 0;
            long rDur = 0;
            try
            {
              rYear = movie.getInt("year");
              rDur = movie.getLong("runtime") * 1000;
            }
            catch (Exception e)
            {
              e.printStackTrace();
            }
            mc.addRow(new Object[]
            {
              movie.getString("movieid"),
              movie.getString("title"),
              movie.getString("tagline"),
              XBMCImageContentProvider.GetImageUri(getBitmapUrl(movie.getString("thumbnail"))).toString(),
              XBMCImageContentProvider.GetImageUri(getBitmapUrl(movie.getString("thumbnail"))).toString(),
              Intent.ACTION_GET_CONTENT,
              Uri.parse("videodb://movies/titles/" + movie.getString("movieid") + "?showinfo=true"),
              0,
              0,
              rYear,
              rDur,
              -1
            });
            nb_movies++;
            totCount++;
          }
        } catch (Exception e)
        {
          e.printStackTrace();
        }
      }
      else if (id.equals(REQ_ID_SHOWS) || ((nb_movies + nb_shows) < 3 && id.equals(REQ_ID_SHOWS_ACTOR)))
      {
        searchtv: try
        {
          if(resp.isNull("result"))
            break searchtv;
          JSONObject results = resp.getJSONObject("result");
          if (results == null || results.isNull("tvshows"))
            break searchtv;
          JSONArray tvshows = results.getJSONArray("tvshows");

          for (int i = 0; i < tvshows.length() && totCount < limit; ++i)
          {
            JSONObject tvshow = tvshows.getJSONObject(i);

            int rYear = 0;
            long rDur = 0;
            try
            {
              rYear = tvshow.getInt("year");
            }
            catch (Exception e)
            {
              e.printStackTrace();
            }
            mc.addRow(new Object[]
            {
              tvshow.getString("tvshowid"),
              tvshow.getString("title"),
              tvshow.getString("plot"),
              XBMCImageContentProvider.GetImageUri(getBitmapUrl(tvshow.getString("thumbnail"))).toString(),
              XBMCImageContentProvider.GetImageUri(getBitmapUrl(tvshow.getString("thumbnail"))).toString(),
              Intent.ACTION_GET_CONTENT,
              Uri.parse("videodb://tvshows/titles/" + tvshow.getString("tvshowid") + "?showinfo=true"),
              0,
              0,
              rYear,
              45*60*1000,
              -1
            });
            Log.d(TAG, "tvshow: " + tvshow.getString("title") + ", " + tvshow.getInt("year"));
            nb_shows++;
            totCount++;
          }
        } catch (Exception e)
        {
          e.printStackTrace();
        }
      }
      else if (id.equals(REQ_ID_ALBUMS))
      {
        searchalbums: try
        {
          if (resp.isNull("result"))
            break searchalbums;
          JSONObject results = resp.getJSONObject("result");
          if (results == null || results.isNull("albums"))
            break searchalbums;
          JSONArray albums = results.getJSONArray("albums");

          for (int i = 0; i < albums.length() && totCount < limit; ++i)
          {
            JSONObject album = albums.getJSONObject(i);
            mc.addRow(new Object[]
            {
              album.getString("albumid"),
              album.getString("title"),
              album.getString("displayartist"),
              XBMCImageContentProvider.GetImageUri(getBitmapUrl(album.getString("thumbnail"))).toString(),
              XBMCImageContentProvider.GetImageUri(getBitmapUrl(album.getString("thumbnail"))).toString(),
              Intent.ACTION_GET_CONTENT,
              Uri.parse("musicdb://albums/" + album.getString("albumid") + "/"),
              0,
              0,
              0,
              0,
              -1
            });
            totCount++;
          }
        } catch (Exception e)
        {
          e.printStackTrace();
        }
      }
      else if (id.equals(REQ_ID_ARTISTS))
      {
        searchartists: try
        {
          if (resp.isNull("result"))
            break searchartists;
          JSONObject results = resp.getJSONObject("result");
          if (results == null || results.isNull("artists"))
            break searchartists;
          JSONArray artists = results.getJSONArray("artists");

          for (int i = 0; i < artists.length() && totCount < limit; ++i)
          {
            JSONObject artist = artists.getJSONObject(i);
            mc.addRow(new Object[]
            {
              artist.getString("artistid"),
              artist.getString("artist"),
              artist.getString("description"),
              XBMCImageContentProvider.GetImageUri(getBitmapUrl(artist.getString("thumbnail"))).toString(),
              XBMCImageContentProvider.GetImageUri(getBitmapUrl(artist.getString("thumbnail"))).toString(),
              Intent.ACTION_GET_CONTENT,
              Uri.parse("musicdb://artists/" + artist.getString("artistid") + "/"),
              0,
              0,
              0,
              0,
              -1
            });
            totCount++;
          }
        } catch (Exception e)
        {
          e.printStackTrace();
        }
      }
    }

    return mc;
  }

  public void updateLeanback(Context ctx)
  {
    if (mNotificationManager == null)
    {
      mNotificationManager = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
    }
    for(Integer id : mRecomendationIds)
      mNotificationManager.cancel(id);
    mRecomendationIds.clear();

    XBMCRecommendationBuilder builder = new XBMCRecommendationBuilder()
        .setContext(ctx)
        .setSmallIcon(R.drawable.notif_icon);

    JSONObject rep = request_object(RECOMMENDATION_MOVIES_JSON);
    if (rep != null && !rep.isNull("result"))
    {
      try
      {
        JSONObject results = rep.getJSONObject("result");
        JSONArray movies = results.getJSONArray("movies");

        int count = 0;
        for (int i = 0; i < movies.length() && count < MAX_RECOMMENDATIONS; ++i)
        {
          try
          {
            JSONObject movie = movies.getJSONObject(i);
            int id = Integer.parseInt(movie.getString("movieid")) + 1000000;

            final XBMCRecommendationBuilder notificationBuilder = builder
                .setBackground(
                    XBMCImageContentProvider.GetImageUri(
                        getBitmapUrl(movie.getString("fanart"))).toString())
                .setId(id).setPriority(MAX_RECOMMENDATIONS - count)
                .setTitle(movie.getString("title"))
                .setDescription(movie.getString("tagline"))
                .setIntent(buildPendingMovieIntent(ctx, movie));

            Bitmap bitmap = getBitmap(movie.getString("thumbnail"));
            notificationBuilder.setBitmap(bitmap);
            Notification notification = notificationBuilder.build();
            mNotificationManager.notify(id, notification);
            mRecomendationIds.add(id);
            ++count;
          } catch (Exception e)
          {
            continue;
          }
        }
      } catch (Exception e)
      {
        e.printStackTrace();
      }
    }

    rep = request_object(RECOMMENDATIONS_SHOWS_JSON);
    if (rep != null && !rep.isNull("result"))
    {
      try
      {
        JSONObject results = rep.getJSONObject("result");
        JSONArray tvshows = results.getJSONArray("tvshows");

        int count = 0;
        for (int i = 0; i < tvshows.length() && count < MAX_RECOMMENDATIONS; ++i)
        {
          try
          {
            JSONObject tvshow = tvshows.getJSONObject(i);
            int id = Integer.parseInt(tvshow.getString("tvshowid")) + 2000000;

            final XBMCRecommendationBuilder notificationBuilder = builder
                .setBackground(
                    XBMCImageContentProvider.GetImageUri(
                        getBitmapUrl(tvshow.getString("fanart"))).toString())
                .setId(id).setPriority(MAX_RECOMMENDATIONS - count)
                .setTitle(tvshow.getString("title"))
                .setDescription(tvshow.getString("plot"))
                .setIntent(buildPendingShowIntent(ctx, tvshow));

            Bitmap bitmap = getBitmap(tvshow.getString("thumbnail"));
            notificationBuilder.setBitmap(bitmap);
            Notification notification = notificationBuilder.build();
            mNotificationManager.notify(id, notification);
            mRecomendationIds.add(id);
            ++count;
          } catch (Exception e)
          {
            continue;
          }

        }
      } catch (Exception e)
      {
        e.printStackTrace();
      }
    }

    rep = request_object(RECOMMENDATIONS_ALBUMS_JSON);
    if (rep != null && !rep.isNull("result"))
    {
      try
      {
        JSONObject results = rep.getJSONObject("result");
        JSONArray albums = results.getJSONArray("albums");

        int count = 0;
        for (int i = 0; i < albums.length() && count < MAX_RECOMMENDATIONS; ++i)
        {
          try
          {
            JSONObject album = albums.getJSONObject(i);
            int id = Integer.parseInt(album.getString("albumid")) + 3000000;

            final XBMCRecommendationBuilder notificationBuilder = builder
                .setBackground(
                    XBMCImageContentProvider.GetImageUri(
                        getBitmapUrl(album.getString("fanart"))).toString())
                .setId(id).setPriority(MAX_RECOMMENDATIONS - count)
                .setTitle(album.getString("title"))
                .setDescription(album.getString("displayartist"))
                .setIntent(buildPendingAlbumIntent(ctx, album));

            Bitmap bitmap = getBitmap(album.getString("thumbnail"));
            notificationBuilder.setBitmap(bitmap);
            Notification notification = notificationBuilder.build();
            mNotificationManager.notify(id, notification);
            mRecomendationIds.add(id);
            ++count;
          } catch (Exception e)
          {
            continue;
          }

        }
      } catch (Exception e)
      {
        e.printStackTrace();
      }
    }

  }

  private PendingIntent buildPendingMovieIntent(Context ctx, JSONObject movie)
  {
    try
    {
      Intent detailsIntent = new Intent(ctx, Splash.class);
      detailsIntent.setAction(Intent.ACTION_GET_CONTENT);
      detailsIntent.setData(Uri.parse("videodb://movies/titles/" + movie.getString("movieid") + "?showinfo=true"));
      //detailsIntent.putExtra(MovieDetailsActivity.MOVIE, movie);
      //detailsIntent.putExtra(MovieDetailsActivity.NOTIFICATION_ID, id);

      return PendingIntent.getActivity(ctx, 0, detailsIntent, PendingIntent.FLAG_CANCEL_CURRENT);
    } catch (Exception e)
    {
      e.printStackTrace();
      return null;
    }
  }

  private PendingIntent buildPendingShowIntent(Context ctx, JSONObject tvshow)
  {
    try
    {
      Intent detailsIntent = new Intent(ctx, Splash.class);
      detailsIntent.setAction(Intent.ACTION_GET_CONTENT);
      detailsIntent.setData(Uri.parse("videodb://tvshows/titles/" + tvshow.getString("tvshowid") + "/"));

      return PendingIntent.getActivity(ctx, 0, detailsIntent, PendingIntent.FLAG_CANCEL_CURRENT);
    } catch (Exception e)
    {
      e.printStackTrace();
      return null;
    }
  }

  private PendingIntent buildPendingAlbumIntent(Context ctx, JSONObject tvshow)
  {
    try
    {
      Intent detailsIntent = new Intent(ctx, Splash.class);
      detailsIntent.setAction(Intent.ACTION_GET_CONTENT);
      detailsIntent.setData(Uri.parse("musicdb://albums/" + tvshow.getString("albumid") + "/"));

      return PendingIntent.getActivity(ctx, 0, detailsIntent, PendingIntent.FLAG_CANCEL_CURRENT);
    } catch (Exception e)
    {
      e.printStackTrace();
      return null;
    }
  }

  // Oreo

  public List<Subscription> getSubscriptions()
  {
    List<Subscription> subscriptions = new ArrayList<Subscription>();

    try
    {
      JSONObject req = request_object(String.format(RETRIEVE_VIDEO_SMARTPLAYLISTS, "1"));

      if (req == null || req.isNull("result"))
        return subscriptions;

      JSONObject results = req.getJSONObject("result");
      JSONArray files = results.getJSONArray("files");

      for (int i = 0; i < files.length(); ++i)
      {
        JSONObject file = files.getJSONObject(i);
        Subscription sub = Subscription.createSubscription(file.getString("label"), file.getString("label"), file.getString("file"), R.drawable.ic_video_80dp);
        subscriptions.add(sub);
      }
    } catch (Exception e)
    {
      e.printStackTrace();
    }

    return subscriptions;
  }

  public List<Media> getMedias(String plUrl)
  {
    List<Media> medias = new ArrayList<Media>();

    try
    {
      JSONObject req = request_object(String.format(RETRIEVE_VIDEO_SMARTPLAYLIST_ITEMS, plUrl, "1"));

      if (req == null || req.isNull("result"))
        return medias;

      JSONObject results = req.getJSONObject("result");
      JSONArray files = results.getJSONArray("files");

      for (int i = 0; i < files.length(); ++i)
      {
        Media med = new Media();
        JSONObject file = files.getJSONObject(i);
        String mediaType = file.getString("type");
        int mediaId = file.getInt("id");
        if (mediaType.equals("movie"))
        {
          JSONObject reqMovie = request_object(String.format(RETRIEVE_MOVIE_DETAILS, mediaId));
          if (reqMovie == null || reqMovie.isNull("result"))
            continue;
          JSONObject details = reqMovie.getJSONObject("result").getJSONObject("moviedetails");

          med.setId(mediaId);
          med.setTitle(details.getString("title"));
          med.setDescription(details.getString("tagline"));
          med.setBackgroundImageUrl(XBMCImageContentProvider.GetImageUri(getBitmapUrl(details.getString("fanart"))).toString());
          med.setCardImageUrl(XBMCImageContentProvider.GetImageUri(getBitmapUrl(details.getString("thumbnail"))).toString());
          med.setVideoUrl("videodb://movies/titles/" + details.getString("movieid") + "?showinfo=true");
          med.setCategory("movie");
        }
        medias.add(med);
      }
    } catch (Exception e)
    {
      e.printStackTrace();
    }

    return medias;
  }
}
