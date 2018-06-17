/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package tw.edu.tku.csie.weatherforecast.sync;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.text.format.DateUtils;

import tw.edu.tku.csie.weatherforecast.data.WeatherAppContract;
import tw.edu.tku.csie.weatherforecast.data.WeatherAppPreferences;
import tw.edu.tku.csie.weatherforecast.utilities.NetworkUtils;
import tw.edu.tku.csie.weatherforecast.utilities.NotificationUtils;
import tw.edu.tku.csie.weatherforecast.utilities.OpenWeatherJsonUtils;

import java.net.URL;

public class SyncTask {

    synchronized public static void syncWeatherData(Context context) {
        try {
            /*
             * The getUrl method will return the URL that we need to get the forecast JSON for the
             * weather. It will decide whether to create a URL based off of the latitude and
             * longitude or off of a simple location as a String.
             */
            URL weatherRequestUrl = NetworkUtils.getUrl(context);

            /* Use the URL to retrieve the JSON */
            String jsonWeatherResponse = NetworkUtils.getResponseFromHttpUrl(weatherRequestUrl);

            /* Parse the JSON into a list of weather values */
            ContentValues[] weatherValues = OpenWeatherJsonUtils
//                    .getWeatherContentValuesFromJson(context, jsonWeatherResponse);
                    .getWeatherContentValuesFromJson(jsonWeatherResponse);

            /*
             * In cases where our JSON contained an error code, getWeatherContentValuesFromJson
             * would have returned null. We need to check for those cases here to prevent any
             * NullPointerExceptions being thrown. We also have no reason to insert fresh data if
             * there isn't any to insert.
             */
            if (weatherValues != null && weatherValues.length != 0) {
                /* Get a handle on the ContentResolver to delete and insert data */
                ContentResolver contentResolver = context.getContentResolver();

                /* Delete old weather data because we don't need to keep multiple days' data */
                contentResolver.delete(
                        WeatherAppContract.WeatherEntry.CONTENT_URI,
                        null,
                        null);

                /* Insert our new weather data into Sunshine's ContentProvider */
                contentResolver.bulkInsert(
                        WeatherAppContract.WeatherEntry.CONTENT_URI,
                        weatherValues);

            }

        } catch (Exception e) {
            /* Server probably invalid */
            e.printStackTrace();
        }
    }

    /**
     * Performs the network request for updated weather, parses the JSON from that request, and
     * inserts the new weather information into our ContentProvider. Will notify the user that new
     * weather has been loaded if the user hasn't been notified of the weather within the last day
     * AND they haven't disabled notifications in the preferences screen.
     *
     * @param context Used to access utility methods and the ContentResolver
     */
    synchronized public static void syncWeather(Context context) {

        syncWeatherData(context);

        /*
         * Finally, after we insert data into the ContentProvider, determine whether or not
         * we should notify the user that the weather has been refreshed.
         */
        boolean notificationsEnabled = WeatherAppPreferences.areNotificationsEnabled(context);

        /*
         * If the last notification was shown was more than 1 day ago, we want to send
         * another notification to the user that the weather has been updated. Remember,
         * it's important that you shouldn't spam your users with notifications.
         */
        long timeSinceLastNotification = WeatherAppPreferences
                .getEllapsedTimeSinceLastNotification(context);

        boolean oneDayPassedSinceLastNotification = false;

        if (timeSinceLastNotification >= DateUtils.DAY_IN_MILLIS) {
            oneDayPassedSinceLastNotification = true;
        }

        /*
         * We only want to show the notification if the user wants them shown and we
         * haven't shown a notification in the past day.
         */
        if (notificationsEnabled && oneDayPassedSinceLastNotification) {
            NotificationUtils.notifyUserOfNewWeather(context);
        }
    }
}