package business.order;

import api.ApiException;
import business.BookstoreDbException;
import business.JdbcUtils;
import business.book.Book;
import business.book.BookDao;
import business.cart.ShoppingCart;
import business.cart.ShoppingCartItem;
import business.customer.Customer;
import business.customer.CustomerDao;
import business.customer.CustomerForm;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.DateTimeException;
import java.time.YearMonth;
import java.util.Calendar;
//import java.util.Date;
import java.sql.Date;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class DefaultOrderService implements OrderService {

	private BookDao bookDao;
	private LineItemDao lineItemDao;
	private OrderDao orderDao;
	private CustomerDao customerDao;

	public void setBookDao(BookDao bookDao) {
		this.bookDao = bookDao;
	}

	public void setLineItemDao(LineItemDao lineItemDao) {
		this.lineItemDao = lineItemDao;
	}

	public void setOrderDao(OrderDao orderDao) {
		this.orderDao = orderDao;
	}

	public void setCustomerDao(CustomerDao customerDao) {
		this.customerDao = customerDao;
	}


	@Override
	public OrderDetails getOrderDetails(long orderId) {
		// NOTE: THIS METHOD PROVIDED NEXT PROJECT
		Order order = orderDao.findByOrderId(orderId);
		Customer customer = customerDao.findByCustomerId(order.customerId());
		List<LineItem> lineItems = lineItemDao.findByOrderId(orderId);
		List<Book> books = lineItems
				.stream()
				.map(lineItem -> bookDao.findByBookId(lineItem.bookId()))
				.toList();
		return new OrderDetails(order, customer, lineItems, books);
	}

	@Override
	public long placeOrder(CustomerForm customerForm, ShoppingCart cart) {

		validateCustomer(customerForm);
		validateCart(cart);

		// NOTE: MORE CODE PROVIDED NEXT PROJECT
		try (Connection connection = JdbcUtils.getConnection()) {
			Date ccExpDate = getCardExpirationDate(
					customerForm.getCcExpiryMonth(),
					customerForm.getCcExpiryYear());
			return performPlaceOrderTransaction(
					customerForm.getName(),
					customerForm.getAddress(),
					customerForm.getPhone(),
					customerForm.getEmail(),
					customerForm.getCcNumber(),
					ccExpDate, cart, connection);
		} catch (SQLException e) {
			throw new BookstoreDbException("Error during close connection for customer order", e);
		}
	}

	private long performPlaceOrderTransaction(
			String name, String address, String phone,
			String email, String ccNumber, Date date,
			ShoppingCart cart, Connection connection) {
		try {
			connection.setAutoCommit(false);
			long customerId = customerDao.create(
					connection, name, address, phone, email,
					ccNumber, date);
			long customerOrderId = orderDao.create(
					connection,
					cart.getComputedSubtotal() + cart.getSurcharge(),
					generateConfirmationNumber(), customerId);
			for (ShoppingCartItem item : cart.getItems()) {
				lineItemDao.create(connection, customerOrderId,
						item.getBookId(), item.getQuantity());
			}
			connection.commit();
			return customerOrderId;
		} catch (Exception e) {
			try {
				connection.rollback();
			} catch (SQLException e1) {
				throw new BookstoreDbException("Failed to roll back transaction", e1);
			}
			return 0;
		}
	}

	private Integer generateConfirmationNumber() {

		return ThreadLocalRandom.current().nextInt(999999999);
	}

	private Date getCardExpirationDate(String monthString, String yearString) {
		int year = Integer.parseInt(yearString);
		int month = Integer.parseInt(monthString) - 1; // Subtract 1 because Calendar months are 0-based

		Calendar calendar = Calendar.getInstance();
		calendar.set(Calendar.YEAR, year);
		calendar.set(Calendar.MONTH, month);
		calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH));
		calendar.set(Calendar.HOUR_OF_DAY, 23);
		calendar.set(Calendar.MINUTE, 59);
		calendar.set(Calendar.SECOND, 59);
		calendar.set(Calendar.MILLISECOND, 999);

		return new java.sql.Date(calendar.getTimeInMillis());
	}


	private void validateCustomer(CustomerForm customerForm) {

		String name = customerForm.getName();

		if (name == null || name.length() < 4 || name.length() > 45) {
			throw new ApiException.ValidationFailure("name", "Invalid name field");
		}

		// Phone Validation
		String phone = customerForm.getPhone();
		if (phone == null || phone.isEmpty() || phone.replaceAll("[\\s\\-()]", "").length() != 10) {
			throw new ApiException.ValidationFailure("phone", "Invalid phone field");
		}

		String address = customerForm.getAddress();
		if (address == null || address.isEmpty() || address.length() < 4 || address.length() > 45) {
			throw new ApiException.ValidationFailure("Invalid address field");
		}

		// Email Validation
		String email = customerForm.getEmail();
		if (email == null || email.isEmpty() || email.contains(" ") || !email.contains("@") || email.endsWith(".")) {
			throw new ApiException.ValidationFailure("Invalid email field");
		}

		// Credit Card Number Validation
		String ccNumber = customerForm.getCcNumber();
		if (ccNumber == null) {
			throw new ApiException.ValidationFailure("Invalid ccNumber field");
		}
		ccNumber = ccNumber.replaceAll("\\s|-", "");
		if (ccNumber.length() < 14 || ccNumber.length() > 16) {
			throw new ApiException.ValidationFailure("Invalid ccNumber field");
		}

		if (expiryDateIsInvalid(customerForm.getCcExpiryMonth(), customerForm.getCcExpiryYear())) {
			throw new ApiException.ValidationFailure("Please enter a valid expiration date.");


		}
	}

	private boolean expiryDateIsInvalid(String ccExpiryMonth, String ccExpiryYear) {

		try {
			int year = Integer.parseInt(ccExpiryYear);
			int month = Integer.parseInt(ccExpiryMonth);

			YearMonth expiryDate = YearMonth.of(year, month);
			YearMonth currentDate = YearMonth.now();

			return expiryDate.isBefore(currentDate);
		} catch (NumberFormatException | DateTimeException e) {
			// Handle invalid format or invalid date
			return true;
		}
	}

	private void validateCart(ShoppingCart cart) {

		if (cart.getItems().size() <= 0) {
			throw new ApiException.ValidationFailure("Cart is empty.");
		}

		cart.getItems().forEach(item -> {
			if (item.getQuantity() <= 0 || item.getQuantity() > 99) {
				throw new ApiException.ValidationFailure("Invalid quantity");
			}
			Book databaseBook = bookDao.findByBookId(item.getBookId());

			if (databaseBook == null) {
				throw new ApiException.ValidationFailure("Book not found for item ID " + item.getBookId());
			}

			// Price Validation
			if (item.getPrice() != databaseBook.price()) {
				throw new ApiException.ValidationFailure("Incorrect price for item ID " + item.getBookId());
			}

			// Category Validation
			if (item.getCategoryId() != databaseBook.categoryId()) {
				throw new ApiException.ValidationFailure("Incorrect category for item ID " + item.getBookId());
			}
		});
	}

}
