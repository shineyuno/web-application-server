package webserver;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.file.Files;
import java.util.Collection;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import db.DataBase;
import model.User;
import util.HttpRequestUtils;
import util.IOUtils;

public class RequestHandler extends Thread {
    private static final Logger log = LoggerFactory.getLogger(RequestHandler.class);

    private Socket connection;

    public RequestHandler(Socket connectionSocket) {
        this.connection = connectionSocket;
    }

    public void run() {
        log.debug("New Client Connect! Connected IP : {}, Port : {}", connection.getInetAddress(),
                connection.getPort());

        try (InputStream in = connection.getInputStream(); OutputStream out = connection.getOutputStream()) {
            // TODO 사용자 요청에 대한 처리는 이 곳에 구현하면 된다.
        		
        		BufferedReader br = new BufferedReader(new InputStreamReader(in, "UTF-8"));
        		
        		
        		String line = "";
        		String[] tokens = null;
        		boolean logined = false;
        		int count = 0;
        		int contentLength = 0;
        		log.info("========== inputStream Start ===========");
        		do{
        			line  = br.readLine();
        			if(line == null) {
        				break;
        			}
        			
        			if(line.contains("Cookie")) {
        				logined = isLogin(line);
        			}
        			
        			
        			//save request Line in tokens
        			if(count == 0) {
        				tokens = line.split(" ");
        			}
        			
        			if(line.contains("Content-Length")) {
        				contentLength = getContentLength(line);
        			}
        			log.info(line);
        			
        			count++;
        			
        			 
        		}while(!"".equals(line));
        		log.info("========== inputStream End ===========");
        		
        		
        		
        		String url = "/index.html";
        		if(tokens != null) {
        			url = tokens[1];
            		log.info("url :" + url);	 
            }
        		
        		
        		
        		if(url.startsWith("/user/create")){
        			String body = IOUtils.readData(br, contentLength);
        			//int index = url.indexOf("?");
        			//String queryString = url.substring(index + 1);
        			Map<String,String> params = HttpRequestUtils.parseQueryString(body);
        			
        			User user = new User();
        			user.setUserId(params.get("userId"));
        			user.setName(params.get("name"));
        			user.setPassword(params.get("password"));
        			user.setEmail(params.get("email"));
        			
        			log.debug("User : {}", user);
        			
        			DataBase.addUser(user);
        			
        			DataOutputStream dos = new DataOutputStream(out);
                    
             
                response302Header(dos, "/index.html");
        		}else if(url.equals("/user/login")) {
        			String body = IOUtils.readData(br, contentLength);
        			Map<String, String> params = HttpRequestUtils.parseQueryString(body);
        			User user = DataBase.findUserById(params.get("userId"));
        			log.debug("user {}",user);
        			if(user == null) {
        				responseResource(out, "/user/login_failed.html");
        			}
        			
        			if(user.getPassword().equals(params.get("password"))) {
        				DataOutputStream dos = new DataOutputStream(out);
        				response302LoginSuccessHeader(dos);
        			} else {
        				responseResource(out, "/user/login_failed.html");
        			}
        			
        		}else if("/user/list".equals(url)) {
        			if(!logined) {
        				responseResource(out,"/user/login.html");
        			}
        			
        			Collection<User> users = DataBase.findAll();
        			StringBuilder sb = new StringBuilder();
        			sb.append("<table border='1'>");
        			for(User user : users) {
        				sb.append("<tr>");
        				sb.append("<td>" + user.getUserId()  +"</td>" );
        				sb.append("<td>" + user.getName()  +"</td>" );
        				sb.append("<td>" + user.getEmail()  +"</td>" );
        				sb.append("<tr>");
        			}
        			sb.append("</table>");
        			byte[] body = sb.toString().getBytes();
        			DataOutputStream dos = new DataOutputStream(out);
        			response200Header(dos, body.length);
        			responseBody(dos,body);
        			
        		}else if(url.endsWith(".css")){
        			DataOutputStream dos = new DataOutputStream(out);
        			byte[] body = Files.readAllBytes(new File("./webapp" + url).toPath());
        			response200CssHeader(dos, body.length);
        			responseBody(dos, body);
        			
        		}else {
        			
        			responseResource(out, url);
        			
                
        		}
        		
        		
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }

    private void response200CssHeader(DataOutputStream dos, int lengthOfBodyContent) {
    		try {
            dos.writeBytes("HTTP/1.1 200 OK \r\n");
            dos.writeBytes("Content-Type: text/css\r\n");
            dos.writeBytes("Content-Length: " + lengthOfBodyContent + "\r\n");
            dos.writeBytes("\r\n");
        } catch (IOException e) {
            log.error(e.getMessage());
        }	
	}

	private boolean isLogin(String line) {
    		String[] headerTokens = line.split(":");
    		Map<String, String> cookies = HttpRequestUtils.parseCookies(headerTokens[1].trim());
    		String value = cookies.get("logined");
    		if(value == null) {
    			return false;
    		}
		return Boolean.parseBoolean(value);
	}

	private void response302LoginSuccessHeader(DataOutputStream dos) {
    	try {
            dos.writeBytes("HTTP/1.1 302 Redirect \r\n");
            dos.writeBytes("Set-Cookie: logined=true \r\n");
            dos.writeBytes("Location: /index.html \r\n");
            dos.writeBytes("\r\n");
        } catch (IOException e) {
            log.error(e.getMessage());
        }
		
	}

	private void responseResource(OutputStream out, String url) throws IOException {
		// TODO Auto-generated method stub
    		DataOutputStream dos = new DataOutputStream(out);
        
        byte[] body = Files.readAllBytes(new File("./webapp" + url).toPath());
        response200Header(dos, body.length);
        responseBody(dos, body);
	}

	private void response302Header(DataOutputStream dos, String url) {
    		try {
            dos.writeBytes("HTTP/1.1 302 Redirect \r\n");
            dos.writeBytes("Location: " + url + " \r\n");
            dos.writeBytes("\r\n");
        } catch (IOException e) {
            log.error(e.getMessage());
        }
		
	}

	private int getContentLength(String line) {
		String[] headerTokens = line.split(":");
		return Integer.parseInt(headerTokens[1].trim());
	}

	private void response200Header(DataOutputStream dos, int lengthOfBodyContent) {
        try {
            dos.writeBytes("HTTP/1.1 200 OK \r\n");
            dos.writeBytes("Content-Type: text/html;charset=utf-8\r\n");
            dos.writeBytes("Content-Length: " + lengthOfBodyContent + "\r\n");
            dos.writeBytes("\r\n");
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }

    private void responseBody(DataOutputStream dos, byte[] body) {
        try {
            dos.write(body, 0, body.length);
            dos.flush();
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }
}
