/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.semperpax.spmc17.channels;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.PersistableBundle;
import android.support.annotation.NonNull;
import android.support.media.tv.Channel;
import android.support.media.tv.PreviewProgram;
import android.support.media.tv.TvContractCompat;
import android.util.Log;

import com.semperpax.spmc17.XBMCJsonRPC;
import com.semperpax.spmc17.channels.model.Media;
import com.semperpax.spmc17.channels.model.Subscription;
import com.semperpax.spmc17.channels.model.XBMCDatabase;
import com.semperpax.spmc17.channels.util.AppLinkHelper;
import com.semperpax.spmc17.channels.util.TvUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Syncs programs for a channel. A channel id is required to be passed via the {@link
 * JobParameters}. This service is scheduled to listen to changes to a channel. Once the job
 * completes, it will reschedule itself to listen for the next change to the channel. See {@link
 * TvUtil#scheduleSyncingProgramsForChannel(Context, long)} for more details about the scheduling.
 */
public class SyncProgramsJobService extends JobService
{

  private static final String TAG = "SyncProgramsJobService";

  private SyncProgramsTask mSyncProgramsTask;

  @Override
  public boolean onStartJob(final JobParameters jobParameters)
  {
    Log.d(TAG, "onStartJob(): " + jobParameters);

    final long channelId = getChannelId(jobParameters);
    if (channelId == -1L)
    {
      return false;
    }
    Log.d(TAG, "onStartJob(): Scheduling syncing for programs for channel " + channelId);

    mSyncProgramsTask =
            new SyncProgramsTask(getApplicationContext())
            {
              @Override
              protected void onPostExecute(Boolean finished)
              {
                super.onPostExecute(finished);
                // Daisy chain listening for the next change to the channel.
                TvUtil.scheduleSyncingProgramsForChannel(
                        SyncProgramsJobService.this, channelId);
                mSyncProgramsTask = null;
                jobFinished(jobParameters, !finished);
              }
            };
    mSyncProgramsTask.execute(channelId);

    return true;
  }

  @Override
  public boolean onStopJob(JobParameters jobParameters)
  {
    if (mSyncProgramsTask != null)
    {
      mSyncProgramsTask.cancel(true);
    }
    return true;
  }

  private long getChannelId(JobParameters jobParameters)
  {
    PersistableBundle extras = jobParameters.getExtras();
    if (extras == null)
    {
      return -1L;
    }

    return extras.getLong(TvContractCompat.EXTRA_CHANNEL_ID, -1L);
  }

  /*
   * Syncs programs by querying the given channel id.
   *
   * If the channel is not browsable, the programs will be removed to avoid showing
   * stale programs when the channel becomes browsable in the future.
   *
   * If the channel is browsable, then it will check if the channel has any programs.
   *      If the channel does not have any programs, new programs will be added.
   *      If the channel does have programs, then a fresh list of programs will be fetched and the
   *          channel's programs will be updated.
   */
  private void syncPrograms(long channelId, String url, List<Media> initialMedias)
  {
    Log.d(TAG, "Sync programs for channel: " + channelId);
    List<Media> medias = new ArrayList<>(initialMedias);

    try (Cursor cursor =
                 getContentResolver()
                         .query(
                                 TvContractCompat.buildChannelUri(channelId),
                                 null,
                                 null,
                                 null,
                                 null))
    {
      if (cursor != null && cursor.moveToNext())
      {
        Channel channel = Channel.fromCursor(cursor);
        if (!channel.isBrowsable())
        {
          Log.d(TAG, "Channel is not browsable: " + channelId);
          deletePrograms(channelId, medias);
        } else
        {
          Log.d(TAG, "Channel is browsable: " + channelId);
          if (medias.isEmpty())
          {
            XBMCJsonRPC jsonrpc = new XBMCJsonRPC();
            medias = createPrograms(channelId, jsonrpc.getMedias(url));
            jsonrpc = null;
          } else
          {
            medias = updatePrograms(channelId, medias);
          }
          XBMCDatabase.saveMedias(getApplicationContext(), channelId, medias);
        }
      }
    }
  }

  private List<Media> createPrograms(long channelId, List<Media> medias)
  {
    List<Media> mediasAdded = new ArrayList<>(medias.size());
    for (Media media : medias)
    {
      PreviewProgram previewProgram = buildProgram(channelId, media);

      Uri programUri =
              getContentResolver()
                      .insert(
                              TvContractCompat.PreviewPrograms.CONTENT_URI,
                              previewProgram.toContentValues());
      long programId = ContentUris.parseId(programUri);
      Log.d(TAG, "Inserted new program: " + programId);
      media.setProgramId(programId);
      mediasAdded.add(media);
    }

    return mediasAdded;
  }

  private List<Media> updatePrograms(long channelId, List<Media> medias)
  {
    for (int i = 0; i < medias.size(); ++i)
    {
      Media old = medias.get(i);
      Media update = medias.get(i);
      long programId = old.getProgramId();

      getContentResolver()
              .update(
                      TvContractCompat.buildPreviewProgramUri(programId),
                      buildProgram(channelId, update).toContentValues(),
                      null,
                      null);
      Log.d(TAG, "Updated program: " + programId);
      update.setProgramId(programId);
    }

    return medias;
  }

  private void deletePrograms(long channelId, List<Media> medias)
  {
    if (medias.isEmpty())
    {
      return;
    }

    int count = 0;
    for (Media media : medias)
    {
      count +=
              getContentResolver()
                      .delete(
                              TvContractCompat.buildPreviewProgramUri(media.getProgramId()),
                              null,
                              null);
    }
    Log.d(TAG, "Deleted " + count + " programs for  channel " + channelId);

    // Remove our local records to stay in sync with the TV Provider.
    XBMCDatabase.removeMedias(getApplicationContext(), channelId);
  }

  @NonNull
  private PreviewProgram buildProgram(long channelId, Media media)
  {
    Uri thumbArtUri = Uri.parse(media.getCardImageUrl());
    Uri posterArtUri = Uri.parse(media.getBackgroundImageUrl());
    Uri appLinkUri = AppLinkHelper.buildPlaybackUri(channelId, media.getId());
    Uri previewVideoUri = Uri.parse(media.getVideoUrl());

    PreviewProgram.Builder builder = new PreviewProgram.Builder();
    builder.setChannelId(channelId)
            .setType(TvContractCompat.PreviewProgramColumns.TYPE_CLIP)
            .setTitle(media.getTitle())
            .setDescription(media.getDescription())
            .setThumbnailUri(thumbArtUri)
            .setPosterArtUri(posterArtUri)
//            .setPreviewVideoUri(previewVideoUri)
            .setIntentUri(appLinkUri);
    return builder.build();
  }

  private class SyncProgramsTask extends AsyncTask<Long, Void, Boolean>
  {

    private final Context mContext;

    private SyncProgramsTask(Context context)
    {
      this.mContext = context;
    }

    @Override
    protected Boolean doInBackground(Long... channelIds)
    {
      List<Long> params = Arrays.asList(channelIds);
      if (!params.isEmpty())
      {
        for (Long channelId : params)
        {
          Subscription subscription =
                  XBMCDatabase.findSubscriptionByChannelId(mContext, channelId);
          if (subscription != null)
          {
            List<Media> cachedMedias = XBMCDatabase.getMedias(mContext, channelId);
            syncPrograms(channelId, subscription.getUrl(), cachedMedias);
          }
        }
      }
      return true;
    }
  }
}
