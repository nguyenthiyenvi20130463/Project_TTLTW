package Control;

import Entity.Order;
import Entity.Product;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Base64;
import java.util.Set;
import java.sql.SQLException;

import Ultis.RSA;
import Ultis.SHA224;
import service.Ulti;
import Dao.ProductDao;
import Dao.OrderDao;
import Entity.Cart;
import Entity.Account;
import java.io.IOException;
import javax.servlet.*;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.Part;
@MultipartConfig
@WebServlet(name = "CheckOrderController", value = { "/CheckOrder" })
public class CheckOrderController extends HttpServlet
{
    protected void doGet(final HttpServletRequest request, final HttpServletResponse response) throws ServletException, IOException {
    }
    
    protected void doPost(final HttpServletRequest request, final HttpServletResponse response) throws ServletException, IOException {
        Account account = (Account)request.getSession().getAttribute("account");
        Cart cart = (Cart)request.getSession().getAttribute("cart");
        if (account == null) {
            response.sendRedirect("Login.jsp");
            return;
        }
        if (cart.getCart().isEmpty()) {
            response.sendRedirect("Cart");
        }
        String username = account.getUsername();
        String paymentText = request.getParameter("payment_method");
        String payment = paymentText.equals("Cod") ? "Thanh toán khi nhận hàng" : null;
        String shipText = request.getParameter("shipping_method");
        String ship = shipText.equals("0") ? "Giao hàng tiết kiệm" : "Viettel Post";
        int totalship = 30000;
        String fullname = request.getParameter("fullname");
        String numberphone = request.getParameter("phone");
        String address = request.getParameter("address_detail")+", "+request.getParameter("phuongxa")+", "+request.getParameter("quanhuyen")+", "+request.getParameter("tinhthanh");
        String comment = request.getParameter("comment");

        OrderDao dao = new OrderDao();
        ProductDao daoProduct = new ProductDao();
        try {
            int total = cart.getTotalSum() + totalship;
            String id_order = Ulti.randomText();
            while(!dao.checkIdOrder(id_order)){
                id_order = Ulti.randomText();
            }

            //Signature
            Order orderSign = new Order(id_order, username, paymentText, shipText, fullname, numberphone, address, total, totalship, comment);

            dao.addOrder(id_order, username, payment, ship, totalship, total, fullname, numberphone, address, comment, cart.getCart().size());
            Set<String> list = cart.getCart().keySet();
            for (String s : list) {
                Product p = daoProduct.getProduct(s);
                dao.addProductOrder(id_order, s, cart.getCart().get(s), p.getPrice_buy() * cart.getCart().get(s));

                //signature
                orderSign.addProductBuy(p.getId(), cart.getCart().get(s));
            }

            //signature
            byte[] privateKeys = null;
            String option = request.getParameter("option"); // Lấy giá trị của option
            if (option.equals("text")) {
                String textData = request.getParameter("textData"); // Lấy dữ liệu từ trường text input
                System.out.println(textData);
                privateKeys = Base64.getDecoder().decode(textData);
            } else if (option.equals("file")) {
                Part filePart = request.getPart("fileData"); // Lấy thông tin của file được tải lên
                InputStream fileContent = filePart.getInputStream(); // Lấy nội dung file dưới dạng InputStream
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(fileContent));
                StringBuilder stringBuilder = new StringBuilder();

                bufferedReader.lines().forEach(stringBuilder::append);
                privateKeys = Base64.getDecoder().decode(stringBuilder.toString());
            }
            if(privateKeys!=null){
                //Hash object
                SHA224 sha224 = new SHA224();
                byte[] objectHash = sha224.convertToByteArray(orderSign);
                String hashingObject = sha224.hashing(objectHash);

                RSA rsa = new RSA();
                String encrypt = rsa.encrypt(hashingObject, privateKeys);
                System.out.println(encrypt);

                OrderDao orderDao = new OrderDao();
                orderDao.updateSignature(id_order, encrypt);
            }

            cart.getCart().clear();
            request.getRequestDispatcher("Success.jsp").forward(request, response);
        }
        catch (SQLException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }
}