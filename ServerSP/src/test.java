
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;

public class test {

	public static void main(String[] args) {

		File file = new File("file.txt");
		try {
			byte[] fileContent = Files.readAllBytes(file.toPath());
			for(byte b : fileContent) {
				System.out.print(b + " ");
			}
			System.out.println("\nfile size = " + fileContent.length);
			
			
			FileOutputStream fos = new FileOutputStream("file2.txt");
			fos.write(fileContent);
			
			byte[] part = new byte[10];
			System.arraycopy(fileContent, 5, part, 0, 10);
			
			fos.write(part);
			fos.close();
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

}
