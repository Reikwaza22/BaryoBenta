package com.baryobenta.community;

import java.util.List;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.http.Field;
import retrofit2.http.FormUrlEncoded;
import retrofit2.http.GET;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;
import retrofit2.http.Query;

/** Every PHP endpoint the app talks to (PHP -> PDO -> PostgreSQL). */
public interface ApiService {

    // ---- accounts ----
    @FormUrlEncoded
    @POST("register.php")
    Call<ApiResponse<AuthData>> register(@Field("full_name") String fullName,
                                         @Field("username") String username,
                                         @Field("email") String email,
                                         @Field("password") String password,
                                         @Field("agree_terms") String agreeTerms);

    @FormUrlEncoded
    @POST("login.php")
    Call<ApiResponse<AuthData>> login(@Field("login") String login,
                                      @Field("password") String password);

    @POST("logout.php")
    Call<ApiResponse<Object>> logout();

    @GET("profile.php")
    Call<ApiResponse<ProfileData>> profile(@Query("user_id") Integer userId);

    @FormUrlEncoded
    @POST("profile.php")
    Call<ApiResponse<ProfileData>> updateProfile(@Field("full_name") String fullName,
                                                 @Field("location") String location);

    @Multipart
    @POST("kyc_submit.php")
    Call<ApiResponse<Object>> submitKyc(@Part("id_type") RequestBody idType,
                                        @Part("id_number") RequestBody idNumber,
                                        @Part MultipartBody.Part idPhoto);

    // ---- listings ----
    @GET("categories.php")
    Call<ApiResponse<List<Category>>> categories();

    @GET("listings.php")
    Call<ApiResponse<List<Listing>>> listings(@Query("q") String query,
                                              @Query("category_id") Integer categoryId,
                                              @Query("mine") Integer mine,
                                              @Query("favorites") Integer favorites,
                                              @Query("seller_id") Integer sellerId);

    @GET("listing.php")
    Call<ApiResponse<Listing>> listing(@Query("id") int id);

    @Multipart
    @POST("listing_create.php")
    Call<ApiResponse<Object>> createListing(@Part("title") RequestBody title,
                                            @Part("category_id") RequestBody categoryId,
                                            @Part("price") RequestBody price,
                                            @Part("description") RequestBody description,
                                            @Part("specs") RequestBody specsJson,
                                            @Part MultipartBody.Part photo);

    @FormUrlEncoded
    @POST("listing_action.php")
    Call<ApiResponse<Object>> listingAction(@Field("listing_id") int listingId,
                                            @Field("action") String action,
                                            @Field("status") String status);

    @FormUrlEncoded
    @POST("favorite_toggle.php")
    Call<ApiResponse<FavoriteState>> toggleFavorite(@Field("listing_id") int listingId);

    // ---- comments / chat ----
    @GET("comments.php")
    Call<ApiResponse<List<CommentItem>>> comments(@Query("listing_id") int listingId);

    @FormUrlEncoded
    @POST("comments.php")
    Call<ApiResponse<List<CommentItem>>> addComment(@Field("listing_id") int listingId,
                                                    @Field("body") String body);

    @GET("conversations.php")
    Call<ApiResponse<List<Conversation>>> conversations();

    @GET("messages.php")
    Call<ApiResponse<List<ChatItem>>> messages(@Query("listing_id") int listingId,
                                               @Query("other_id") int otherId);

    @FormUrlEncoded
    @POST("messages.php")
    Call<ApiResponse<List<ChatItem>>> sendMessage(@Field("listing_id") int listingId,
                                                  @Field("other_id") int otherId,
                                                  @Field("body") String body);

    @FormUrlEncoded
    @POST("messages.php")
    Call<ApiResponse<List<ChatItem>>> react(@Field("listing_id") int listingId,
                                            @Field("other_id") int otherId,
                                            @Field("action") String action,
                                            @Field("message_id") int messageId,
                                            @Field("reaction") String reaction);

    @Multipart
    @POST("messages.php")
    Call<ApiResponse<List<ChatItem>>> sendMedia(@Part("listing_id") RequestBody listingId,
                                                @Part("other_id") RequestBody otherId,
                                                @Part("type") RequestBody type,
                                                @Part("duration") RequestBody duration,
                                                @Part MultipartBody.Part file);

    // ---- wallet (digital credits) ----
    @GET("wallet.php")
    Call<ApiResponse<WalletData>> wallet();

    @FormUrlEncoded
    @POST("wallet.php")
    Call<ApiResponse<WalletData>> topUp(@Field("amount") String amount,
                                        @Field("payment_method") String paymentMethod,
                                        @Field("payment_reference") String paymentReference);

    // ---- trust: reviews, reports, transactions ----
    @FormUrlEncoded
    @POST("review_add.php")
    Call<ApiResponse<Object>> addReview(@Field("listing_id") int listingId,
                                        @Field("rating") int rating,
                                        @Field("comment") String comment);

    @FormUrlEncoded
    @POST("report_add.php")
    Call<ApiResponse<Object>> addReport(@Field("listing_id") int listingId,
                                        @Field("reason") String reason,
                                        @Field("details") String details);

    @GET("listing_buyers.php")
    Call<ApiResponse<List<BuyerItem>>> buyers(@Query("listing_id") int listingId);

    @FormUrlEncoded
    @POST("transactions.php")
    Call<ApiResponse<Object>> markSold(@Field("listing_id") int listingId,
                                       @Field("buyer_id") int buyerId);

    @GET("transactions.php")
    Call<ApiResponse<List<TransactionItem>>> transactions();
}
