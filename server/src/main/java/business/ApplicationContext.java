package business;

import business.book.BookDao;
import business.book.BookDaoJdbc;
import business.category.CategoryDao;
import business.category.CategoryDaoJdbc;
import business.customer.CustomerDao;
import business.customer.CustomerDaoJdbc;
import business.order.*;

public class ApplicationContext {

    private CategoryDao categoryDao;
    private BookDao bookDao;
    private OrderService orderService;
    private LineItemDao lineItemDao;
    private OrderDao orderDao;
    private CustomerDao customerDao;


    public static ApplicationContext INSTANCE = new ApplicationContext();

    public LineItemDao getLineItemDao() {
        return lineItemDao;
    }

    public OrderDao getOrderDao() {
        return orderDao;
    }

    public CustomerDao getCustomerDao() {
        return customerDao;
    }

    private ApplicationContext() {
        categoryDao = new CategoryDaoJdbc();
        bookDao = new BookDaoJdbc();
        orderService = new DefaultOrderService();
        lineItemDao = new LineItemDaoJdbc();
        orderDao = new OrderDaoJdbc();
        customerDao = new CustomerDaoJdbc();

        ((DefaultOrderService) orderService).setBookDao(bookDao);
        ((DefaultOrderService) orderService).setOrderDao(orderDao);
        ((DefaultOrderService) orderService).setCustomerDao(customerDao);
        ((DefaultOrderService) orderService).setLineItemDao(lineItemDao);
    }


    public CategoryDao getCategoryDao() {
        return categoryDao;
    }

    public BookDao getBookDao() {
        return bookDao;
    }

    public OrderService getOrderService() {
        return orderService;

    }
}
