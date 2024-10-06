package com.example.webchatserver;

import com.example.webchatserver.FileReaderWriter;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Response;
import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Map;

@Path("/history")
public class ChatLogResource {
    @GET
    @Path("/{roomID}")
    @Produces("application/json")
    public Response getRoomHistory(@PathParam("roomID") String roomID) {

        URL url = this.getClass().getClassLoader().getResource("/chatHistory");
        String history = "";
        File mainDir = null;

        try {
            mainDir = new File(url.toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }

        // load the file content into history
        try {
            history = FileReaderWriter.readHistoryFile(mainDir, roomID + ".json");
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }

        // build json data for the response
        JSONObject mapper = new JSONObject();
        mapper.put("room", roomID);
        if(history!=null){
            mapper.put("log", history);
        }else{
            mapper.put("log", "");
        }

        Response myResp = Response.status(200) // success
                .header("Content-Type", "application/json")
                .entity(mapper.toString()) // adding the json data
                .build();
        return myResp;


    }


    @POST
    @Path("/{roomID}")
    @Consumes("application/json")
    @Produces("application/json")
    public Response saveRoomHistory(@PathParam("roomID") String roomID, String content) {

        // parse the consumed json data
        System.out.println(content);
        JSONObject mapper = new JSONObject(content);
        Map<String,Object> result = mapper.toMap();
        String filename = (String) result.get("room");

        URL url = this.getClass().getClassLoader().getResource("/chatHistory");

        File data = null;
        try {
            System.out.println(url.toURI());
            data = new File(url.toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }

        try {
            // save the chat log history to the roomID.json file in the resources folder
            FileReaderWriter.saveNewFile(data, filename+".json", (String) result.get("log"));
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }

        Response myResp = Response.status(200) // success
                .header("Content-Type", "application/json")
                .build();
        return myResp;
    }

}