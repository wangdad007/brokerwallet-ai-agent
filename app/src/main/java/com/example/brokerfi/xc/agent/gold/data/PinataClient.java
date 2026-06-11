package com.example.brokerfi.xc.agent.gold.data;

import org.json.JSONObject;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

/**
 * IPFS 存储服务客户端 (通过 Pinata API 实现)
 */
public class PinataClient {
    // 请替换为你自己在 Pinata (pinata.cloud) 申请的真实 JWT 令牌
    private static final String PINATA_JWT = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VySW5mb3JtYXRpb24iOnsiaWQiOiI5OTJiZTVmMi1jNmU5LTQ1MTItYTcwMi02ODRiMDI2M2QxNWYiLCJlbWFpbCI6InRhbmd5dWNpbmRlckBnbWFpbC5jb20iLCJlbWFpbF92ZXJpZmllZCI6dHJ1ZSwicGluX3BvbGljeSI6eyJyZWdpb25zIjpbeyJkZXNpcmVkUmVwbGljYXRpb25Db3VudCI6MSwiaWQiOiJGUkExIn0seyJkZXNpcmVkUmVwbGljYXRpb25Db3VudCI6MSwiaWQiOiJOWUMxIn1dLCJ2ZXJzaW9uIjoxfSwibWZhX2VuYWJsZWQiOmZhbHNlLCJzdGF0dXMiOiJBQ1RJVkUifSwiYXV0aGVudGljYXRpb25UeXBlIjoic2NvcGVkS2V5Iiwic2NvcGVkS2V5S2V5IjoiOWI2OTFiYWIxNjMyNzYzN2ViM2EiLCJzY29wZWRLZXlTZWNyZXQiOiJhMjI5NjgzYjExZTcyZTkyM2EzZWY4ZGE2Mjg3YzUxOGQ0MzU3ZTUzYmZkNGIzMTQ2Nzg5NWE0OWUwMGNkNTA1IiwiZXhwIjoxODEyNjg2NDU0fQ.L8q0458K1d5Y3IY1AW8vqALx1Ph5oFWd3ak7d0fUjhE";
    
    // IPFS 网关地址，用于读取数据
    private static final String IPFS_GATEWAY = "https://gateway.pinata.cloud/ipfs/";

    /**
     * 将 JSON 元数据上传到 IPFS
     * @return 返回文件的 CID (哈希值)
     */
    public static String uploadJsonToIPFS(JSONObject jsonMetadata) throws Exception {
        URL url = new URL("https://api.pinata.cloud/pinning/pinJSONToIPFS");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Authorization", "Bearer " + PINATA_JWT);
        conn.setDoOutput(true);

        try (OutputStream os = conn.getOutputStream()) {
            byte[] input = jsonMetadata.toString().getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }

        if (conn.getResponseCode() != 200) {
            throw new Exception("Pinata 上传失败: " + conn.getResponseMessage());
        }

        try (Scanner scanner = new Scanner(conn.getInputStream(), "UTF-8")) {
            String response = scanner.useDelimiter("\\A").next();
            JSONObject resJson = new JSONObject(response);
            return resJson.getString("IpfsHash");
        }
    }

    /**
     * 从 IPFS 根据 CID 下载 JSON 文本
     */
    public static String downloadJsonFromIPFS(String cid) throws Exception {
        if (cid == null || cid.isEmpty()) return "{}";
        URL url = new URL(IPFS_GATEWAY + cid);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);

        if (conn.getResponseCode() != 200) {
            throw new Exception("IPFS 读取失败: " + conn.getResponseMessage());
        }

        try (Scanner scanner = new Scanner(conn.getInputStream(), "UTF-8")) {
            return scanner.useDelimiter("\\A").next();
        }
    }
}
