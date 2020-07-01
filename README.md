# YTNotify

Get YouTube channel updates on your desktop!

YTNotify is a simple Java application that periodically checks YouTube channels that you specify and notifies you of any updates.

## How to Use it
### Initial setup

YTNotify requires two things: [Simple JSON](https://mvnrepository.com/artifact/com.googlecode.json-simple/json-simple/1.1.1)'s jar file in the `lib` folder and a Google API key with YouTube enabled in `key.txt`. From there you can build using the `.classpath` file (if your IDE supports it) or by running `javac` with the `lib` folder in your classpath. The same goes for running it: just have `javaw` run the generated `bin/YTNotify.class` with `lib` in the classpath.

### General Usage

Once the program is running, an icon will show up in the taskbar at the bottom right (usually) of the screen. Just right click on that and select "Add by Name", "Add by ID", or "Search by Name" and follow the instructions. Once you've entered a new channel, any updates to that channel will show up as notifications on your screen. When you get a notification you can right click on the taskbar icon again and go over to the "Recent Updates" menu to have the new video show up in your web browser. The "View Channels" button will bring up a menu that lets you visit a channel's page, remove channels, and mark channels as "infrequent", meaning that they are checked less often, which saves your quota.

Note that "Search by Name" should be used sparingly as it eats up API quota.

## How it Works

Once you give YTNotify the name or ID of the channel that you want to follow, it contacts Google's YouTube API and gets the channel's upload playlist ID. From there, it periodically queries the API and if the newest video's title has changed, it knows that there's a new video.

## Issues/Questions

### Why isn't there an API key included?

Each API key gets a limited "quota" per day that it can use before the API will lock the key out for the rest of the day. While one key is good enough for up to a few users, eventually enough people would bring it down. This way, everyone has their own quota.

### Why doesn't clicking on the notification bring up the video?

There IS supposed to be a way to have notifications send actions to Java listeners, but the documentation somehow left out HOW this can be done (probably because a lot of this stuff is platform-dependent). I've tried some ideas from places like StackExchange like adding extra `ActionListener`s to the `TrayIcon`, but nothing seems to work. If someone out there knows how to do this, please tell me and I will add it in.
