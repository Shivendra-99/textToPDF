package com.example.textToPDF;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@SpringBootApplication
public class TextToPdfApplication implements RequestHandler<S3Event, Map<String, Object>>{
	private final S3Client s3Client = S3Client.builder().build();
	Map<String, Object> response = new HashMap<>();

	public static void main(String[] args) {
		SpringApplication.run(TextToPdfApplication.class, args);
	}
	
	@Override
	public Map<String, Object> handleRequest(S3Event event, Context context) {

		// Get the bucket name and Object from the event
		// Note -: Object is the name of the file that uploaded in the bucket
		String bucketName = event.getRecords().get(0).getS3().getBucket().getName();
		String Object = event.getRecords().get(0).getS3().getObject().getKey();

		context.getLogger().log("Bucket Name: " + bucketName);
		context.getLogger().log("Object Name: " + Object);

		// Download the file from the S3 bucket

		context.getLogger().log("Downloading the file from the S3 bucket");

		GetObjectRequest objectRequest = GetObjectRequest.builder().bucket(bucketName).key(Object).build();
		ResponseInputStream<GetObjectResponse> texObjectResponse = s3Client.getObject(objectRequest);
		context.getLogger().log("Download completed from s3 bucket");

		try {
			BufferedReader fileContent = new BufferedReader(
					new InputStreamReader(texObjectResponse, StandardCharsets.UTF_8));
			context.getLogger().log("Starting the conversion of the file to PDF");
			String outputFileName = "/tmp/"+Object.replace(".txt", ".pdf").replace("+", " ");
			PdfWriter writer = new PdfWriter(outputFileName);
			PdfDocument pdf = new PdfDocument(writer);
			Document document = new Document(pdf);
			String line;
			while ((line = fileContent.readLine()) != null) {
				document.add(new Paragraph(line));
			}
			document.close();
			context.getLogger().log("File converted to PDF successfully");
			String OutPutFile = outputFileName.replace("/tmp/", "");
			PutObjectRequest putObjectRequest = PutObjectRequest.builder()
					.bucket(bucketName)
					.key(OutPutFile)
					.build();
			//Removing the /temp and adding the file name to the outputFileName
			
			s3Client.putObject(putObjectRequest, RequestBody.fromFile(new File(outputFileName)));
			context.getLogger().log("PDF uploaded to the S3 bucket successfully");
			response.put("statusCode", 200);
			return response;
		} catch (Exception e) {
			context.getLogger().log("Error in downloading the file from the S3 bucket");
			context.getLogger().log(e.getMessage());
			response.put("statusCode", 500);
			return response;
		}
	}
}

