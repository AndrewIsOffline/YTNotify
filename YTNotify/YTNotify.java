import java.awt.AWTException;
import java.awt.Desktop;
import java.awt.Image;
import java.awt.Menu;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.Toolkit;
import java.awt.TrayIcon;
import java.awt.TrayIcon.MessageType;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.ConnectException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Scanner;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JOptionPane;

import org.json.simple.*;
import org.json.simple.parser.JSONParser;

public class YTNotify {
    //TODO: Hold on to the previous updates between runs.
    //TODO: Comment everything.

    final int MAXMEM = 20; //Maximum amount of updates saved.

    final int GETVID = 0;
    final int FINDNAME = 1;
    final int FINDID = 2;

    SystemTray tray;
    ArrayList<DataNode> data;
    String apikey;
    String[] linkmem, memids, memnames;
    MenuItem[] linkbtns;

    int stack;
    boolean running = true;

    YTNotify() {
        //Load data from files.
        try {
            Scanner scn = new Scanner(new File("key.txt"));
            apikey = scn.nextLine();
            scn.close();
        } catch(Exception e) {
            System.err.println("Error reading API key!");
            e.printStackTrace();
            System.exit(1);
        }
        data = new ArrayList<DataNode>();
        try {
            Scanner file = new Scanner(new File("data.txt"));
            ArrayList<String> rawdata = new ArrayList<String>();
            while (file.hasNext())
                rawdata.add(file.nextLine());
            file.close();

            for (int i = 0; i < rawdata.size() / 4; i++) {
                DataNode newdata = new DataNode();
                newdata.name = rawdata.get(i * 4);
                newdata.id = rawdata.get(i * 4 + 1);
                newdata.ulid = rawdata.get(i * 4 + 2);
                newdata.lastvid = rawdata.get(i * 4 + 3);
                data.add(newdata);
            }
        } catch (Exception e) {
            //The data file hasn't been created yet. Just ignore.
            //Or, some file system error happened, in which case there's not much the program can do, anyways.
        }

        //Initialize the tray icon.
        tray = SystemTray.getSystemTray();
        PopupMenu popup = new PopupMenu();
        Image img = Toolkit.getDefaultToolkit().getImage("NotifyICO.png");
        TrayIcon trayIcon = new TrayIcon(img, "YTNotify", popup);
        trayIcon.setActionCommand("YTNotify");
        trayIcon.setImageAutoSize(true);

        //Initialize the update memory.
        linkmem = new String[MAXMEM];
        memids = new String[MAXMEM];
        memnames = new String[MAXMEM];
        linkbtns = new MenuItem[MAXMEM];

        //Initialize the memory buttons.
        Menu memmenu = new Menu("Recent updates");
        for (int i = 0; i < MAXMEM; i++) {
            linkbtns[i] = new MenuItem((i + 1) + ": None");
            linkbtns[i].addActionListener(new ActionListener() {
                int pos;

                @Override
                public void actionPerformed(ActionEvent e) {
                    if (linkmem[pos] == null)
                        return;
                    try {
                        Desktop.getDesktop().browse(new URL("https://www.youtube.com/watch?v=" + memids[pos]).toURI());
                    } catch (Exception e1) {
                        e1.printStackTrace();
                    }
                }

                ActionListener init(int p) {
                    pos = p;
                    return this;
                }
            }.init(i));
            memmenu.add(linkbtns[i]);
        }

        //Initialize the other buttons.
        MenuItem itemAdd = new MenuItem("Add by Name");
        itemAdd.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                addChannel(false);
            }
        });
        MenuItem itemAddID = new MenuItem("Add by ID");
        itemAddID.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                addChannel(true);
            }
        });
        MenuItem itemEnd = new MenuItem("End Program");
        itemEnd.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                running = false;
            }
        });

        //Add everything to the tray.
        popup.add(memmenu);
        popup.add(itemAdd);
        popup.add(itemAddID);
        popup.add(itemEnd);
        trayIcon.setPopupMenu(popup);
        try {
            tray.add(trayIcon);
        } catch (AWTException e) {
            e.printStackTrace();
            running = false;
        }

        //Main loop
        while (running) {
            forloop: for (int i = 0; i < data.size(); i++) {
                stack = 0;

                //Check for updates.
                DataNode pos = data.get(i);
                JSONObject json = getYTJSON(pos.ulid, GETVID);
                if (json == null) {
                    trayIcon.displayMessage("Error!", "YouTube refused last request! Did you reach your quota?",
                            MessageType.ERROR);
                    break forloop;
                }
                if (json.get("error") != null) {
                    trayIcon.displayMessage("Error!", "YouTube returned an error! Did you reach your quota?",
                            MessageType.ERROR);
                    break forloop;
                }
                JSONObject vid = (JSONObject)((JSONArray)json.get("items")).get(0);
                JSONObject snip = (JSONObject)vid.get("snippet");
                JSONObject rid = (JSONObject)snip.get("resourceId");
                String vidid = (String)rid.get("videoId");
                String title = (String)snip.get("title");

                //Update found!
                if (!title.equals(pos.lastvid)) {
                    trayIcon.displayMessage("New video from " + pos.name, title, MessageType.INFO);
                    pos.lastvid = title;
                    for (int j = MAXMEM - 1; j > 0; j--) {
                        linkmem[j] = linkmem[j - 1];
                        memids[j] = memids[j - 1];
                        memnames[j] = memnames[j - 1];
                        linkbtns[j].setLabel((j + 1) + ": " + linkmem[j] + " - " + memnames[j]);
                    }
                    linkmem[0] = title;
                    memids[0] = vidid;
                    memnames[0] = pos.name;
                    linkbtns[0].setLabel("1: " + linkmem[0] + " - " + memnames[0]);
                    saveData();
                }
                try {
                    Thread.sleep(10000); //10 second spacer between each individual channel check.
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            try {
                Thread.sleep(3600000); //Delay between checks.
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        tray.remove(trayIcon);
    }

    //Reads everything in a Reader to a single String.
    private static String readAll(Reader read) throws IOException {
        StringBuilder sb = new StringBuilder();
        int in = read.read();
        while (in != -1) {
            sb.append((char) in);
            in = read.read();
        }
        return sb.toString();
    }

    //Gets data from the API and converts it into a JSON object.
    private JSONObject getYTJSON(String input, int type) {
        JSONObject json = null;
        InputStream is = null;
        try {
            switch (type) {
                case GETVID:
                    is = new URL("https://www.googleapis.com/youtube/v3/playlistItems?part=snippet&playlistId=" + input + "&maxResults=1&key=" + apikey).openStream();
                    break;
                case FINDNAME:
                    is = new URL("https://www.googleapis.com/youtube/v3/channels?part=snippet%2CcontentDetails&forUsername=" + input + "&maxResults=1&key=" + apikey).openStream();
                    break;
                case FINDID:
                    is = new URL("https://www.googleapis.com/youtube/v3/channels?part=snippet%2CcontentDetails&id=" + input + "&maxResults=1&key=" + apikey).openStream();
                    break;
            }
        } catch (ConnectException e) {
            if(e.getMessage().indexOf("timed out") > -1 && stack < 5) {
                stack++;
                return getYTJSON(input, type);
            }
            System.out.println("HTTP Error! Malformed request? Quota reached?");
            e.printStackTrace();
            return null;
        } catch (MalformedURLException e) {
            System.out.println("HTTP Error! Malformed request? Quota reached?");
            e.printStackTrace();
            return null;
        } catch (IOException e) {
            System.out.println("HTTP Error! Malformed request? Quota reached?");
            e.printStackTrace();
            return null;
        }
        try {
            BufferedReader rd = new BufferedReader(new InputStreamReader(is, Charset.forName("UTF-8")));
            String jsonText = readAll(rd);
            json = (JSONObject)new JSONParser().parse(jsonText);
            //System.out.println(json.toString());
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                is.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return json;
    }

    //Writes the internal array of data to a file.
    void saveData() {
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(new File("data.txt")));
            for(int i = 0; i < data.size(); i++) {
                writer.write(data.get(i).name + "\n");
                writer.write(data.get(i).id + "\n");
                writer.write(data.get(i).ulid + "\n");
                writer.write(data.get(i).lastvid + "\n");
            }
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //Adds a new channel.
    void addChannel(boolean byID) {
        //Get channel-identifying information.
        JSONObject channeldata = null;
        if(byID) {
            String searchid = JOptionPane.showInputDialog("Channel ID?");
            if (searchid == null || searchid.trim().equals("")) {
                JOptionPane.showMessageDialog(null, "No ID entered!", "Error!", JOptionPane.ERROR_MESSAGE);
                return;
            }
            channeldata = getYTJSON(searchid, FINDID);
        } else {
            String searchname = JOptionPane.showInputDialog("Channel name?");
            if (searchname == null || searchname.trim().equals("")) {
                JOptionPane.showMessageDialog(null, "No name entered!", "Error!", JOptionPane.ERROR_MESSAGE);
                return;
            }
            channeldata = getYTJSON(searchname, FINDNAME);
        }
        if(channeldata == null) {
            JOptionPane.showMessageDialog(null, "YouTube refused request! Did you type something wrong?", "Error!", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if(channeldata.get("error") != null) {
            JOptionPane.showMessageDialog(null, "YouTube returned an error! Did you type something wrong?", "Error!", JOptionPane.ERROR_MESSAGE);
            return;
        }

        //Get and process information from the API.
        JSONObject res = (JSONObject)channeldata.get("pageInfo");
        long results = (long)res.get("totalResults");
        if (results == 0) {
            JOptionPane.showMessageDialog(null, "No results found!", "Error!", JOptionPane.ERROR_MESSAGE);
            return;
        }
        JSONObject resone = (JSONObject)((JSONArray)channeldata.get("items")).get(0);
        JSONObject snippet = (JSONObject)(resone.get("snippet"));
        JSONObject thumbs = (JSONObject)(snippet.get("thumbnails"));
        Image thumb = null;
        try {
            URL url = new URL((String)((JSONObject)thumbs.get("default")).get("url"));
            thumb = ImageIO.read(url);
        } catch (Exception e1) {
            e1.printStackTrace();
            return;
        }
        String chname = (String)snippet.get("title");
        int ans = JOptionPane.showConfirmDialog(null, "Is it " + chname + "?", "Confirm", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, new ImageIcon(thumb));
        if(ans != JOptionPane.YES_OPTION) return;
        DataNode newdata = new DataNode();
        newdata.name = chname;
        newdata.id = (String)resone.get("id");
        for(int i = 0; i < data.size(); i++) {
            if(data.get(i).id.equals(newdata.id)) {
                JOptionPane.showMessageDialog(null, "That channel is already being watched!", "Error!", JOptionPane.ERROR_MESSAGE);
            }
        }
        JSONObject cd = (JSONObject)resone.get("contentDetails");
        JSONObject rp = (JSONObject)cd.get("relatedPlaylists");
        newdata.ulid = (String)rp.get("uploads");
        JSONObject json = getYTJSON(newdata.ulid, GETVID);
        if(json == null || ((JSONArray)json.get("items")).size() == 0) {
            //TODO: Create special case allowing for empty channels.
            JOptionPane.showMessageDialog(null, "YouTube returned an error! Did you type something wrong?", "Error!", JOptionPane.ERROR_MESSAGE);
            return;
        }
        JSONObject vid = (JSONObject)((JSONArray)json.get("items")).get(0);
        JSONObject snip = (JSONObject)vid.get("snippet");
        newdata.lastvid = (String)snip.get("title");
        data.add(newdata);
        saveData();
    }

    public static void main(String[] args) {
        new YTNotify();
    }
}

class DataNode {
    String name, id, ulid, lastvid;
}
