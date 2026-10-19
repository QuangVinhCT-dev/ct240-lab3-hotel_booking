package com.ctu.service;

import com.ctu.exception.PaymentFailedException;
import com.ctu.exception.RoomNotAvailableException;
import com.ctu.model.BookingReceipt;
import com.ctu.model.Room;
import com.ctu.repository.RoomRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("BookingService Unit Test Suite")
class BookingServiceTest {

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private PaymentGateway paymentGateway;

    @InjectMocks
    private BookingService bookingService;

    @Test
    @DisplayName("Đặt phòng thành công - Cập nhật trạng thái phòng và thanh toán")
    void testBookRoom_Success() {
        Room room = new Room("R101", 500.0, true);
        when(roomRepository.findById("R101")).thenReturn(Optional.of(room));
        when(paymentGateway.charge("1234-5678", 1000.0)).thenReturn(true);

        BookingReceipt receipt = bookingService.bookRoom("R101", 2, "1234-5678");

        assertNotNull(receipt);
        assertEquals("R101", receipt.getRoomId());
        assertEquals(1000.0, receipt.getTotalAmount());
        assertFalse(room.isAvailable(), "Trạng thái phòng phải chuyển thành false");

        verify(paymentGateway, times(1)).charge("1234-5678", 1000.0);
        verify(roomRepository, times(1)).save(room);
    }

    @Test
    @DisplayName("Ném RoomNotAvailableException khi không tìm thấy phòng")
    void testBookRoom_RoomNotFound_ThrowsException() {
        when(roomRepository.findById("R999")).thenReturn(Optional.empty());

        assertThrows(RoomNotAvailableException.class, () -> {
            bookingService.bookRoom("R999", 2, "1234-5678");
        });

        verify(paymentGateway, never()).charge(anyString(), anyDouble());
        verify(roomRepository, never()).save(any());
    }

    @Test
    @DisplayName("Ném RoomNotAvailableException khi phòng đã được đặt trước đó")
    void testBookRoom_RoomAlreadyBooked_ThrowsException() {
        Room bookedRoom = new Room("R101", 500.0, false);
        when(roomRepository.findById("R101")).thenReturn(Optional.of(bookedRoom));

        assertThrows(RoomNotAvailableException.class, () -> {
            bookingService.bookRoom("R101", 2, "1234-5678");
        });

        verify(paymentGateway, never()).charge(anyString(), anyDouble());
        verify(roomRepository, never()).save(any());
    }

    @Test
    @DisplayName("Ném PaymentFailedException khi thanh toán thất bại")
    void testBookRoom_PaymentFailed_ThrowsException() {
        Room room = new Room("R101", 500.0, true);
        when(roomRepository.findById("R101")).thenReturn(Optional.of(room));
        when(paymentGateway.charge("1234-5678", 1000.0)).thenReturn(false);

        assertThrows(PaymentFailedException.class, () -> {
            bookingService.bookRoom("R101", 2, "1234-5678");
        });

        assertTrue(room.isAvailable());
        verify(roomRepository, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, -5})
    @DisplayName("Ném IllegalArgumentException khi số đêm nghỉ <= 0")
    void testBookRoom_InvalidNights_ThrowsException(int invalidNights) {
        assertThrows(IllegalArgumentException.class, () -> {
            bookingService.bookRoom("R101", invalidNights, "1234-5678");
        });
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("Ném IllegalArgumentException khi thẻ tín dụng bị null hoặc rỗng")
    void testBookRoom_NullOrEmptyCreditCard_ThrowsException(String invalidCard) {
        assertThrows(IllegalArgumentException.class, () -> {
            bookingService.bookRoom("R101", 2, invalidCard);
        });
    }

    @Test
    @DisplayName("Ném IllegalArgumentException khi roomId bị null")
    void testBookRoom_NullRoomId_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            bookingService.bookRoom(null, 2, "1234-5678");
        });
    }
}