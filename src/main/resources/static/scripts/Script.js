// Premium Front-End Scripting - Novel Reader Platform

$(document).ready(function() {

    // --- Toast Notification Helper ---
    function showToast(message, type = "success") {
        var toastEl = document.getElementById('actionToast');
        if (!toastEl) return;
        
        var toastBody = document.getElementById('toast-message-body');
        toastBody.innerText = message;
        
        // Reset classes
        toastEl.className = "toast align-items-center text-white border-0";
        
        if (type === "success") {
            toastEl.classList.add("bg-toast-success");
        } else if (type === "error") {
            toastEl.classList.add("bg-toast-error");
        } else {
            toastEl.classList.add("bg-toast-info");
        }
        
        var toast = new bootstrap.Toast(toastEl, { delay: 3000 });
        toast.show();
    }

    // Check if redirect query asks to show login page
    var urlParams = new URLSearchParams(window.location.search);
    if (urlParams.has('showLogin')) {
        document.body.classList.add("show-popup");
        showToast("Please sign in or register to access this page.", "info");
    }

    // Check for PayU payment redirect parameters
    if (urlParams.get('payment') === 'success') {
        showToast("Payment successful! Snow Flakes added to your wallet.", "success");
        window.history.replaceState({}, document.title, window.location.pathname);
    } else if (urlParams.get('payment') === 'failure') {
        var reason = urlParams.get('reason') || "Transaction declined/cancelled.";
        showToast("Payment failed: " + reason, "error");
        window.history.replaceState({}, document.title, window.location.pathname);
    }

    // Initialize search filter UI states from hidden inputs on page load
    var initType = $("#search-filter-type-val").val() || "ALL";
    var initGenre = $("#search-filter-genre-val").val() || "ALL";
    var initYear = $("#search-filter-year-val").val() || "ALL";
    var initSort = $("#search-filter-sort-val").val() || "POPULARITY";
    var initStatus = $("#search-filter-status-val").val() || "ALL";
    var initTags = $("#search-filter-tags-val").val() || "ALL";
    var initCountry = $("#search-filter-country-val").val() || "ALL";
    var initSource = $("#search-filter-source-val").val() || "ALL";
    var initEditorSelection = $("#search-filter-editor-val").val() || "ALL";

    function initCustomEditorDropdown() {
        var $realSelect = $("#search-filter-editor-select");
        var $customWrapper = $(".custom-search-select-wrapper");
        var $trigger = $(".custom-search-select-trigger");
        var $triggerText = $trigger.find(".selected-value");
        var $dropdown = $(".custom-search-select-dropdown");
        var $searchInput = $(".custom-search-select-search-input");
        var $optionsContainer = $(".custom-search-select-options");

        // Close dropdown when clicking outside
        $(document).on("click", function(e) {
            if (!$(e.target).closest(".custom-search-select-wrapper").length) {
                $dropdown.removeClass("active");
                $trigger.removeClass("active");
            }
        });

        // Toggle dropdown
        $trigger.on("click", function(e) {
            e.stopPropagation();
            $dropdown.toggleClass("active");
            $(this).toggleClass("active");
            if ($dropdown.hasClass("active")) {
                $searchInput.val("").trigger("input").focus();
            }
        });

        // Search filter input behavior
        $searchInput.on("click", function(e) {
            e.stopPropagation();
        });

        $searchInput.on("input", function() {
            var query = $(this).val().toLowerCase().trim();
            $optionsContainer.find(".custom-search-select-option").each(function() {
                var text = $(this).text().toLowerCase();
                if (text.indexOf(query) > -1) {
                    $(this).show();
                } else {
                    $(this).hide();
                }
            });
        });

        // Option selection
        $optionsContainer.on("click", ".custom-search-select-option", function(e) {
            e.stopPropagation();
            var val = $(this).data("value");
            var text = $(this).text();

            $optionsContainer.find(".custom-search-select-option").removeClass("active");
            $(this).addClass("active");

            $triggerText.text(text);
            $dropdown.removeClass("active");
            $trigger.removeClass("active");

            $realSelect.val(val).trigger("change");
        });

        window.syncCustomEditorDropdown = function() {
            var val = $realSelect.val() || "ALL";
            var $activeOpt = $optionsContainer.find('.custom-search-select-option[data-value="' + val + '"]');
            if ($activeOpt.length) {
                $optionsContainer.find(".custom-search-select-option").removeClass("active");
                $activeOpt.addClass("active");
                $triggerText.text($activeOpt.text());
            }
        };

        window.syncCustomEditorDropdown();
    }

    initCustomEditorDropdown();

    $("#search-filter-genre-select").val(initGenre);
    $("#search-filter-year-select").val(initYear);
    $("#search-filter-sort-select").val(initSort);
    $("#search-filter-status-select").val(initStatus);
    $("#search-filter-tags-select").val(initTags);
    $("#search-filter-country-select").val(initCountry);
    $("#search-filter-source-select").val(initSource);
    $("#search-filter-editor-select").val(initEditorSelection);
    if (typeof window.syncCustomEditorDropdown === 'function') window.syncCustomEditorDropdown();

    // Expand search bar on page load if search is active
    var currentSearchVal = $(".search-bar .input").val();
    if (currentSearchVal && currentSearchVal.trim() !== "") {
        $(".search-bar, .search-bar .input").addClass("active");
        $("#search-filter-icon-btn").show();
    }

    // --- Search bar behavior ---
    $(".fa-search").click(function() {
        var input = $(".search-bar .input");
        if (input.hasClass("active")) {
            $("#search-form").submit();
        } else {
            $(".search-bar, .search-bar .input").toggleClass("active");
            if ($(".search-bar").hasClass("active")) {
                input.focus();
                $("#search-filter-icon-btn").show();
            } else {
                $("#search-filter-icon-btn").hide();
                $("#search-filter-panel").addClass("d-none");
            }
        }
    });

    // Toggle search filter overlay panel
    $("#search-filter-icon-btn").click(function(e) {
        e.stopPropagation();
        $("#search-filter-panel").toggleClass("d-none");
        if (!$("#search-filter-panel").hasClass("d-none")) {
            $("#search-results-dropdown").addClass("d-none");
        }
    });

    // Stop propagation of clicks inside the filter panel
    $("#search-filter-panel").click(function(e) {
        e.stopPropagation();
    });

    // Filter Genre selection
    $("#search-filter-genre-select").change(function() {
        $("#search-filter-genre-val").val($(this).val());
        triggerSearchQuery();
    });

    // Filter Year selection
    $("#search-filter-year-select").change(function() {
        $("#search-filter-year-val").val($(this).val());
        triggerSearchQuery();
    });

    // Filter Sort selection
    $("#search-filter-sort-select").change(function() {
        $("#search-filter-sort-val").val($(this).val());
        triggerSearchQuery();
    });

    // Filter Status selection
    $("#search-filter-status-select").change(function() {
        $("#search-filter-status-val").val($(this).val());
        triggerSearchQuery();
    });

    // Filter Tags selection
    $("#search-filter-tags-select").change(function() {
        $("#search-filter-tags-val").val($(this).val());
        triggerSearchQuery();
    });

    // Filter Country selection
    $("#search-filter-country-select").change(function() {
        $("#search-filter-country-val").val($(this).val());
        triggerSearchQuery();
    });

    // Filter Source selection
    $("#search-filter-source-select").change(function() {
        $("#search-filter-source-val").val($(this).val());
        triggerSearchQuery();
    });

    // Filter Editor Selection
    $("#search-filter-editor-select").change(function() {
        $("#search-filter-editor-val").val($(this).val());
        triggerSearchQuery();
    });

    function triggerSearchQuery() {
        var query = $(".search-bar .input").val().trim();
        if (query.length >= 1) {
            $(".search-bar .input").trigger("input");
        }
    }

    // Retract search bar, dropdown and filter panel if clicked outside
    $(document).click(function(e) {
        var searchBar = $(".search-bar");
        var dropdown = $("#search-results-dropdown");
        var filterPanel = $("#search-filter-panel");
        if (!searchBar.is(e.target) && searchBar.has(e.target).length === 0 &&
            !dropdown.is(e.target) && dropdown.has(e.target).length === 0 &&
            !filterPanel.is(e.target) && filterPanel.has(e.target).length === 0) {
            
            dropdown.addClass("d-none").empty();
            filterPanel.addClass("d-none");
            if (searchBar.hasClass("active")) {
                var input = searchBar.find(".input");
                var hasSearchVal = input.val() && input.val().trim().length > 0;
                
                if (!hasSearchVal) {
                    searchBar.removeClass("active");
                    input.removeClass("active");
                    $("#search-filter-icon-btn").hide();
                }
            }
        }
    });

    // Apply filters button handler
    $("#btn-apply-filters").click(function() {
        $("#search-form").submit();
    });

    // Reset filters button handler
    $("#btn-clear-filters").click(function() {
        // Reset selectors
        $("#search-filter-genre-select").val("ALL");
        $("#search-filter-year-select").val("ALL");
        $("#search-filter-sort-select").val("POPULARITY");
        $("#search-filter-status-select").val("ALL");
        $("#search-filter-tags-select").val("ALL");
        $("#search-filter-country-select").val("ALL");
        $("#search-filter-source-select").val("ALL");
        $("#search-filter-editor-select").val("ALL");
        if (typeof window.syncCustomEditorDropdown === 'function') window.syncCustomEditorDropdown();

        // Reset hidden inputs
        $("#search-filter-type-val").val("ALL");
        $("#search-filter-genre-val").val("ALL");
        $("#search-filter-year-val").val("ALL");
        $("#search-filter-sort-val").val("POPULARITY");
        $("#search-filter-status-val").val("ALL");
        $("#search-filter-tags-val").val("ALL");
        $("#search-filter-country-val").val("ALL");
        $("#search-filter-source-val").val("ALL");
        $("#search-filter-editor-val").val("ALL");

        // Submit form
        $("#search-form").submit();
    });

    // Real-time search dropdown suggestions on typing 1+ characters or focus
    var searchTimeout = null;
    $(".search-bar .input").on("focus input", function() {
        var query = $(this).val().trim();
        var dropdown = $("#search-results-dropdown");
        
        if (searchTimeout) {
            clearTimeout(searchTimeout);
        }
        
        if (query.length >= 1) {
            searchTimeout = setTimeout(function() {
                var searchGenre = $("#search-filter-genre-val").val() || "ALL";
                var searchYear = $("#search-filter-year-val").val() || "ALL";
                var searchSort = $("#search-filter-sort-val").val() || "POPULARITY";
                var searchStatus = $("#search-filter-status-val").val() || "ALL";
                var searchTags = $("#search-filter-tags-val").val() || "ALL";
                var searchCountry = $("#search-filter-country-val").val() || "ALL";
                var searchSource = $("#search-filter-source-val").val() || "ALL";
                var searchEditor = $("#search-filter-editor-val").val() || "ALL";
                $.getJSON("/api/novels", { 
                    search: query,
                    genre: searchGenre,
                    year: searchYear,
                    sort: searchSort,
                    status: searchStatus,
                    tags: searchTags,
                    country: searchCountry,
                    source: searchSource,
                    editor: searchEditor
                }, function(data) {
                    dropdown.empty();
                    
                    if (!data || data.length === 0) {
                        dropdown.html('<div class="p-3 text-muted text-center font-size-sm">No results found</div>');
                        dropdown.removeClass("d-none");
                        return;
                    }
                    
                    var matchingAuthors = {};
                    var matchingGenres = {};
                    var qLower = query.toLowerCase();
                    
                    // Group results by type (only if TITLE matches)
                    var groups = {
                        "NOVEL": [],
                        "COMIC": [],
                        "MANHWA": [],
                        "MANGA": []
                    };
                    
                    data.forEach(function(item) {
                        var title = (item.title || "").toLowerCase();
                        var author = (item.author || "");
                        var genreStr = (item.genre || "");
                        
                        // 1. Author matching
                        if (author.toLowerCase().indexOf(qLower) !== -1) {
                            matchingAuthors[author] = (matchingAuthors[author] || 0) + 1;
                        }
                        
                        // 2. Genre matching
                        var genres = genreStr.split(",").map(function(g) { return g.trim(); });
                        genres.forEach(function(g) {
                            if (g.toLowerCase().indexOf(qLower) !== -1) {
                                matchingGenres[g] = (matchingGenres[g] || 0) + 1;
                            }
                        });
                        
                        // 3. Title matching (for grouped thumbnail list)
                        if (title.indexOf(qLower) !== -1) {
                            var type = (item.type || "").toUpperCase();
                            if (groups[type]) {
                                groups[type].push(item);
                            } else {
                                if (!groups["OTHER"]) groups["OTHER"] = [];
                                groups["OTHER"].push(item);
                            }
                        }
                    });
                    
                    var html = "";
                    
                    // A. Search Action Header
                    html += '<a href="#" class="search-action-item" id="search-action-submit">' +
                            '  <span class="action-text"><i class="fa-solid fa-arrow-turn-down fa-rotate-90 me-2"></i>Search for <strong>' + query + '</strong></span>' +
                            '  <i class="fa fa-arrow-right item-arrow"></i>' +
                            '</a>';
                            
                    // B. Author Rows
                    Object.keys(matchingAuthors).forEach(function(author) {
                        var count = matchingAuthors[author];
                        html += '<a href="/?search=' + encodeURIComponent(author) + '" class="search-badge-item">' +
                                '  <div class="badge-content">' +
                                '    <span class="search-badge badge-author">Author</span>' +
                                '    <span class="item-name">' + author + '</span>' +
                                '    <span class="item-count">' + count + '</span>' +
                                '  </div>' +
                                '  <i class="fa fa-arrow-right item-arrow"></i>' +
                                '</a>';
                    });
                    
                    // C. Genre Rows
                    Object.keys(matchingGenres).forEach(function(genre) {
                        var count = matchingGenres[genre];
                        html += '<a href="/?genre=' + encodeURIComponent(genre) + '" class="search-badge-item">' +
                                '  <div class="badge-content">' +
                                '    <span class="search-badge badge-genre">Genre</span>' +
                                '    <span class="item-name">' + genre + '</span>' +
                                '    <span class="item-count">' + count + '</span>' +
                                '  </div>' +
                                '  <i class="fa fa-arrow-right item-arrow"></i>' +
                                '</a>';
                    });
                    
                    // D. Grouped Series (only if titles match query)
                    var typeLabels = {
                        "NOVEL": "Novels",
                        "COMIC": "Comics",
                        "MANHWA": "Manhwa",
                        "MANGA": "Manga",
                        "OTHER": "Other"
                    };
                    
                    var hasItems = false;
                    Object.keys(groups).forEach(function(key) {
                        var list = groups[key];
                        if (list && list.length > 0) {
                            hasItems = true;
                            var label = typeLabels[key] || key;
                            html += '<div class="search-group-header">' + label + '</div>';
                            html += '<div class="search-results-card-container">';
                            list.slice(0, 5).forEach(function(item) {
                                html += '<a href="/novel/' + item.id + '" class="search-result-card">' +
                                        '  <div class="search-result-card-img-wrapper">' +
                                        '    <img src="' + (item.coverUrl || '/uploads/default-cover.jpg') + '" class="search-result-card-img" alt="">' +
                                        '  </div>' +
                                        '  <div class="search-result-card-info">' +
                                        '    <div class="search-result-card-title">' + item.title + '</div>' +
                                        '  </div>' +
                                        '</a>';
                            });
                            html += '</div>';
                        }
                    });
                    
                    // If no authors, genres, or titles match
                    if (Object.keys(matchingAuthors).length === 0 && Object.keys(matchingGenres).length === 0 && !hasItems) {
                        dropdown.html('<div class="p-3 text-muted text-center font-size-sm">No results found</div>');
                        dropdown.removeClass("d-none");
                        return;
                    }
                    
                    // Footer link
                    html += '<a href="#" class="search-results-footer" id="btn-view-all-results">View all results for <strong>' + query + '</strong></a>';
                    
                    dropdown.html(html);
                    dropdown.removeClass("d-none");
                    
                    // Hook search-action-submit click to submit form
                    $("#search-action-submit").click(function(e) {
                        e.preventDefault();
                        $("#search-form").submit();
                    });
                    
                    // Hook view-all click to submit form
                    $("#btn-view-all-results").click(function(e) {
                        e.preventDefault();
                        $("#search-form").submit();
                    });
                });
            }, 250);
        } else {
            dropdown.addClass("d-none").empty();
        }
    });

    $(".search-bar .input").on("keydown", function(e) {
        var dropdown = $("#search-results-dropdown");
        if (dropdown.hasClass("d-none")) return;

        var items = dropdown.find(".search-result-card, .search-badge-item, .search-action-item, .search-results-footer");
        if (items.length === 0) return;

        var activeIndex = -1;
        items.each(function(index, el) {
            if ($(el).hasClass("highlighted-item")) {
                activeIndex = index;
            }
        });

        if (e.key === "ArrowDown") {
            e.preventDefault();
            items.removeClass("highlighted-item");
            var nextIndex = (activeIndex + 1) % items.length;
            var nextItem = $(items[nextIndex]);
            nextItem.addClass("highlighted-item");
            
            // Scroll into view
            var container = dropdown[0];
            var elem = nextItem[0];
            if (elem.offsetTop < container.scrollTop) {
                container.scrollTop = elem.offsetTop;
            } else if (elem.offsetTop + elem.offsetHeight > container.scrollTop + container.clientHeight) {
                container.scrollTop = elem.offsetTop + elem.offsetHeight - container.clientHeight;
            }
        } else if (e.key === "ArrowUp") {
            e.preventDefault();
            items.removeClass("highlighted-item");
            var prevIndex = (activeIndex - 1 + items.length) % items.length;
            var prevItem = $(items[prevIndex]);
            prevItem.addClass("highlighted-item");
            
            // Scroll into view
            var container = dropdown[0];
            var elem = prevItem[0];
            if (elem.offsetTop < container.scrollTop) {
                container.scrollTop = elem.offsetTop;
            } else if (elem.offsetTop + elem.offsetHeight > container.scrollTop + container.clientHeight) {
                container.scrollTop = elem.offsetTop + elem.offsetHeight - container.clientHeight;
            }
        } else if (e.key === "Enter") {
            if (activeIndex !== -1) {
                e.preventDefault();
                var href = $(items[activeIndex]).attr("href");
                if (href && href !== "#") {
                    window.location.href = href;
                } else {
                    $(items[activeIndex])[0].click();
                }
            }
        }
    });

    // --- Sign In / Sign Up Modal Controls ---
    $(".log").click(function() {
        document.body.classList.toggle("show-popup");
        if (typeof resetSignupForm === 'function') resetSignupForm();
    });

    $(".blur-bg-overlay").click(function() {
        document.body.classList.remove("show-popup");
        if (typeof resetSignupForm === 'function') resetSignupForm();
    });

    document.addEventListener("keydown", (event) => {
        if (event.key === "Escape") {
            document.body.classList.remove("show-popup");
            if (typeof resetSignupForm === 'function') resetSignupForm();
        }
    });

    // Toggle panels in Login/Signup Modal
    $('#signup, #to-signup-btn').click(function(e) {
        e.preventDefault();
        $('#container').addClass('right-panel-active');
        if (typeof resetSignupForm === 'function') resetSignupForm();
    });

    $('#login, #to-login-btn').click(function(e) {
        e.preventDefault();
        $('#container').removeClass('right-panel-active');
        if (typeof resetSignupForm === 'function') resetSignupForm();
    });

    // --- AJAX Authentication Operations ---

    // Transition for forgot password
    $(document).on("click", "#link-forgot-password", function(e) {
        e.preventDefault();
        $("#login-credentials-section").hide();
        $("#login-forgot-request-section").fadeIn();
    });

    $(document).on("click", "#link-forgot-back-to-login", function(e) {
        e.preventDefault();
        $("#login-forgot-request-section").hide();
        $("#login-credentials-section").fadeIn();
    });

    $(document).on("click", "#link-forgot-reset-back-to-login", function(e) {
        e.preventDefault();
        $("#login-forgot-reset-section").hide();
        $("#login-credentials-section").fadeIn();
    });

    // Login Form Submit (handles standard login, forgot request, and forgot reset)
    $("#login-form-modal").submit(function(e) {
        e.preventDefault();

        if ($("#login-credentials-section").is(":visible")) {
            var email = $("#login-email").val();
            var password = $("#login-password").val();
            var rememberMe = $("#login-remember-me").is(":checked");

            if (!email || !email.trim() || !password) {
                showToast("Email and password are required.", "warning");
                return;
            }

            $.post("/api/auth/login", {
                email: email,
                password: password,
                rememberMe: rememberMe
            })
            .done(function(res) {
                showToast("Welcome back, " + res.user.name + "!");
                setTimeout(function() {
                    location.reload();
                }, 1200);
            })
            .fail(function(err) {
                var msg = err.responseJSON && err.responseJSON.error ? err.responseJSON.error : "Invalid login credentials.";
                showToast(msg, "error");
            });
        } 
        else if ($("#login-forgot-request-section").is(":visible")) {
            var email = $("#forgot-email").val();
            if (!email || !email.trim()) {
                showToast("Email is required to request reset code.", "warning");
                return;
            }

            showToast("Sending reset code...", "info");
            $.post("/api/auth/forgot-password/request", {
                email: email
            })
            .done(function(res) {
                showToast(res.message);
                $("#login-forgot-request-section").hide();
                $("#login-forgot-reset-section").fadeIn();
            })
            .fail(function(err) {
                var msg = err.responseJSON && err.responseJSON.error ? err.responseJSON.error : "Failed to send verification code.";
                showToast(msg, "error");
            });
        } 
        else if ($("#login-forgot-reset-section").is(":visible")) {
            var email = $("#forgot-email").val();
            var otp = $("#forgot-otp").val();
            var newPassword = $("#forgot-new-password").val();

            if (!otp || !otp.trim() || !newPassword) {
                showToast("Verification code and new password are required.", "warning");
                return;
            }

            showToast("Resetting password...", "info");
            $.post("/api/auth/forgot-password/reset", {
                email: email,
                otp: otp,
                newPassword: newPassword
            })
            .done(function(res) {
                showToast(res.message);
                setTimeout(function() {
                    $("#login-forgot-reset-section").hide();
                    $("#login-credentials-section").fadeIn();
                    $("#login-email").val(email);
                    $("#login-password").val("");
                }, 1500);
            })
            .fail(function(err) {
                var msg = err.responseJSON && err.responseJSON.error ? err.responseJSON.error : "Failed to reset password.";
                showToast(msg, "error");
            });
        }
    });

    // Signup Form Submit
    function requestVerificationCode() {
        var name = $("#signup-name").val();
        var email = $("#signup-email").val();
        var password = $("#signup-password").val();
        var user_type = $("#signup-role").val() || "READER";

        if (!name || !name.trim() || !email || !email.trim() || !password) {
            showToast("All fields are required.", "warning");
            return;
        }

        showToast("Sending verification code...", "info");

        $.post("/api/auth/signup/send-code", {
            name: name,
            email: email,
            password: password,
            user_type: user_type
        })
        .done(function(res) {
            showToast(res.message, "success");
            $("#signup-initial-fields").fadeOut(200, function() {
                $("#signup-verification-fields").fadeIn(200);
            });
        })
        .fail(function(err) {
            var msg = err.responseJSON && err.responseJSON.error ? err.responseJSON.error : "Failed to send code.";
            showToast(msg, "error");
        });
    }

    $("#signup-form-modal").submit(function(e) {
        e.preventDefault();
        
        if ($("#signup-verification-fields").is(":visible")) {
            var code = $("#signup-verification-code").val();
            if (!code || !code.trim()) {
                showToast("Please enter the verification code.", "warning");
                return;
            }
            
            $.post("/api/auth/signup/verify", { code: code.trim() })
            .done(function(res) {
                showToast("Account created successfully! Logged in as " + res.user.name, "success");
                setTimeout(function() {
                    location.reload();
                }, 1200);
            })
            .fail(function(err) {
                var msg = err.responseJSON && err.responseJSON.error ? err.responseJSON.error : "Verification failed.";
                showToast(msg, "error");
            });
        } else {
            requestVerificationCode();
        }
    });

    $("#btn-resend-signup-code").click(function(e) {
        e.preventDefault();
        requestVerificationCode();
    });

    function resetSignupForm() {
        $("#signup-verification-fields").hide();
        $("#signup-initial-fields").show();
        $("#signup-verification-code").val("");
    }
    // Logout Click
    $(document).on("click", "#btn-logout", function(e) {
        e.preventDefault();
        $.post("/api/auth/logout")
        .done(function() {
            showToast("Logged out successfully.");
            setTimeout(function() {
                window.location.href = "/";
            }, 1000);
        });
    });


    // --- Bookshelf & Bookmarks Actions ---

    // Toggle Bookshelf Button (Details Page)
    $("#btn-bookshelf-toggle, #add-novel-collection-btn").click(function() {
        var btn = $(this);
        var novelId = btn.data("id");

        $.post("/api/bookmarks/toggle", { novelId: novelId })
        .done(function(res) {
            var icon = btn.find("i");
            if (res.bookmarked) {
                icon.removeClass("fa-bookmark-o").addClass("fa-bookmark text-violet");
                btn.find("span").text(res.reading ? "Reading" : "Bookmarked");
                showToast("Added to your Bookshelf.");
            } else {
                icon.removeClass("fa-bookmark text-violet").addClass("fa-bookmark-o");
                btn.find("span").text("Add Bookmark");
                showToast("Removed from your Bookshelf.");
            }
        })
        .fail(function(err) {
            if (err.status === 401) {
                // Not logged in -> trigger auth modal
                document.body.classList.add("show-popup");
                showToast("Please sign in to add this series to your bookshelf.", "info");
            } else {
                showToast("Error updating bookshelf.", "error");
            }
        });
    });

    // Remove Bookmark from Bookshelf Grid View
    $(".btn-bookshelf-remove-card").click(function() {
        var btn = $(this);
        var novelId = btn.data("id");
        var card = $("#bookmark-card-" + novelId);

        $.post("/api/bookmarks/toggle", { novelId: novelId })
        .done(function(res) {
            showToast("Removed from bookshelf.");
            card.fadeOut(400, function() {
                card.remove();
                
                // Update dynamic bookshelf count text
                var count = $(".bookshelf-card").length;
                $("#bookshelf-count-text").text("You have saved " + count + " " + (count === 1 ? "series" : "series") + " to your collection.");

                // Check if bookshelf is now completely empty
                if (count === 0) {
                    location.reload(); // Reload to trigger empty state block
                }
            });
        })
        .fail(function() {
            showToast("Error removing item.", "error");
        });
    });


    // --- Client-Side Snappy Filter Engine (Home Page) ---
    var selectedType = $("#search-filter-type-val").val() || "NOVEL";
    var selectedGenre = $("#search-filter-genre-val").val() || "ALL";
    var itemsPerPage = 12;
    var currentPage = 1;
    var totalPages = 1;

    function applyFilters() {
        var cards = $(".book-card-col");
        var matchedCards = [];

        // 1. Determine matching cards
        cards.each(function() {
            var cardCol = $(this);
            var type = cardCol.data("type");
            var genresStr = cardCol.data("genres") || "";
            var genres = genresStr.split(",").map(g => g.trim().toUpperCase());

            var typeMatch = (selectedType === "ALL" || type === selectedType);
            var genreMatch = (selectedGenre === "ALL" || genres.includes(selectedGenre.toUpperCase()));

            if (typeMatch && genreMatch) {
                matchedCards.push(cardCol);
            } else {
                cardCol.hide();
            }
        });

        var visibleCount = matchedCards.length;
        totalPages = Math.ceil(visibleCount / itemsPerPage) || 1;

        // Keep currentPage within bounds
        if (currentPage > totalPages) {
            currentPage = totalPages;
        }
        if (currentPage < 1) {
            currentPage = 1;
        }

        // 2. Paginate show/hide
        var startIndex = (currentPage - 1) * itemsPerPage;
        var endIndex = startIndex + itemsPerPage;

        for (var i = 0; i < matchedCards.length; i++) {
            if (i >= startIndex && i < endIndex) {
                matchedCards[i].fadeIn(300);
            } else {
                matchedCards[i].hide();
            }
        }

        // 3. Update Pagination UI
        if (visibleCount === 0) {
            $("#discovery-pagination").addClass("d-none");
            $("#empty-state-container").removeClass("d-none").fadeIn(300);
        } else {
            $("#empty-state-container").addClass("d-none");
            renderPagination(currentPage, totalPages);
        }
    }

    function renderPagination(activePage, totalPages) {
        var container = $("#discovery-pagination");
        container.empty();

        if (totalPages <= 1) {
            container.addClass("d-none");
            return;
        }
        container.removeClass("d-none");

        // 1. Double Left chevron button "<<"
        var btnFirst = $('<div class="yuki-pagination-btn" data-page="first">«</div>');
        if (activePage === 1) {
            btnFirst.addClass("disabled");
        }
        container.append(btnFirst);

        // 2. Single Left chevron button "<"
        var btnPrev = $('<div class="yuki-pagination-btn" data-page="prev">‹</div>');
        if (activePage === 1) {
            btnPrev.addClass("disabled");
        }
        container.append(btnPrev);

        // 3. Page Numbers list logic
        var pagesToShow = [];
        if (totalPages <= 7) {
            for (var i = 1; i <= totalPages; i++) {
                pagesToShow.push(i);
            }
        } else {
            if (activePage <= 4) {
                pagesToShow = [1, 2, 3, 4, 5, "ellipsis", totalPages];
            } else if (activePage >= totalPages - 3) {
                pagesToShow = [1, "ellipsis", totalPages - 4, totalPages - 3, totalPages - 2, totalPages - 1, totalPages];
            } else {
                pagesToShow = [1, "ellipsis", activePage - 1, activePage, activePage + 1, "ellipsis", totalPages];
            }
        }

        pagesToShow.forEach(function(p) {
            if (p === "ellipsis") {
                container.append('<div class="yuki-pagination-ellipsis">...</div>');
            } else {
                var btnPage = $('<div class="yuki-pagination-btn" data-page="' + p + '">' + p + '</div>');
                if (p === activePage) {
                    btnPage.addClass("active");
                }
                container.append(btnPage);
            }
        });

        // 4. Single Right chevron button ">"
        var btnNext = $('<div class="yuki-pagination-btn" data-page="next">›</div>');
        if (activePage === totalPages) {
            btnNext.addClass("disabled");
        }
        container.append(btnNext);

        // 5. Double Right chevron button ">>"
        var btnLast = $('<div class="yuki-pagination-btn" data-page="last">»</div>');
        if (activePage === totalPages) {
            btnLast.addClass("disabled");
        }
        container.append(btnLast);

        // 6. Text info: Page X of Y
        var textInfo = $('<div class="yuki-pagination-info">Page <strong>' + activePage + '</strong> of <strong>' + totalPages + '</strong></div>');
        container.append(textInfo);
    }

    function scrollToFilterSection() {
        var targetOffset = $(".filter-section").offset();
        if (targetOffset) {
            $('html, body').animate({
                scrollTop: targetOffset.top - 20
            }, 300);
        }
    }

    // Delegated click handler for Snappy Filter Engine pagination buttons
    $(document).on("click", "#discovery-pagination .yuki-pagination-btn:not(.disabled):not(.active)", function() {
        var pageAttr = $(this).attr("data-page");
        if (pageAttr === "first") {
            currentPage = 1;
        } else if (pageAttr === "prev") {
            currentPage--;
        } else if (pageAttr === "next") {
            currentPage++;
        } else if (pageAttr === "last") {
            currentPage = totalPages;
        } else {
            currentPage = parseInt(pageAttr);
        }
        applyFilters();
        scrollToFilterSection();
    });

    // Tab Type Selection
    $(".filter-tab").click(function() {
        $(".filter-tab").removeClass("active");
        $(this).addClass("active");
        selectedType = $(this).data("type");
        $("#search-filter-type-val").val(selectedType); // Sync hidden input
        currentPage = 1;
        applyFilters();
    });

    // Nav-dropdown items filter trigger (from Explore menu)
    $(".genre-filter-nav").click(function(e) {
        e.preventDefault();
        var targetType = $(this).data("type");
        if (window.location.pathname !== "/") {
            // If not on home, redirect to home with query parameters
            window.location.href = "/?type=" + targetType;
            return;
        }
        // Active format tab matching selection
        $(".filter-tab").removeClass("active");
        if (targetType === "NOVEL") {
            $("#tab-novels").addClass("active");
        } else if (targetType === "COMIC") {
            $("#tab-comics").addClass("active");
        } else if (targetType === "MANHWA") {
            $("#tab-manhwa").addClass("active");
        } else if (targetType === "MANGA") {
            $("#tab-manga").addClass("active");
        } else {
            $("#tab-all").addClass("active");
        }
        selectedType = targetType;
        currentPage = 1;
        applyFilters();
    });

    // Check url params for homepage filtering
    if (window.location.pathname === "/") {
        var filterParam = urlParams.get("type");
        if (filterParam) {
            selectedType = filterParam.toUpperCase();
            $(".filter-tab").removeClass("active");
            if (selectedType === "NOVEL") $("#tab-novels").addClass("active");
            if (selectedType === "COMIC") $("#tab-comics").addClass("active");
            if (selectedType === "MANHWA") $("#tab-manhwa").addClass("active");
            if (selectedType === "MANGA") $("#tab-manga").addClass("active");
            currentPage = 1;
            applyFilters();
        }
        var genreParam = urlParams.get("genre");
        if (genreParam) {
            selectedGenre = genreParam;
            $(".genre-chip").removeClass("active");
            $(".genre-chip").each(function() {
                if ($(this).data("genre").toLowerCase() === genreParam.toLowerCase()) {
                    $(this).addClass("active");
                }
            });
            currentPage = 1;
            applyFilters();
        }
    }

    // Genre Chip Selection
    $(".genre-chip").click(function() {
        $(".genre-chip").removeClass("active");
        $(this).addClass("active");
        selectedGenre = $(this).data("genre");
        $("#search-filter-genre-val").val(selectedGenre); // Sync hidden input
        $("#search-filter-genre-select").val(selectedGenre); // Sync dropdown
        currentPage = 1;
        applyFilters();
    });

    // Run filters once on initial load (so default is also paginated)
    if (window.location.pathname === "/") {
        applyFilters();
    }


    // --- Novel Reader Settings Panel ---

    // Toggle setting panel drawer
    $("#btn-reader-settings").click(function(e) {
        e.stopPropagation();
        $("#reader-settings-panel-drawer").toggleClass("d-none");
    });

    // Close settings if click outside
    $(document).click(function(e) {
        var panel = $("#reader-settings-panel-drawer");
        if (!panel.is(e.target) && panel.has(e.target).length === 0 && !$("#btn-reader-settings").is(e.target)) {
            panel.addClass("d-none");
        }
    });

    // Load persisted configurations on reader load
    if ($("#novel-reading-frame").length > 0) {
        var textBody = $("#reader-content-text-body");
        
        // Font Family
        var savedFont = localStorage.getItem("reader-font") || "Lora";
        textBody.css("font-family", "'" + savedFont + "', serif");
        $(".font-selector-btn").removeClass("active");
        $(".font-selector-btn[data-font='" + savedFont + "']").addClass("active");

        // Font Size
        var savedSize = parseInt(localStorage.getItem("reader-size")) || 18;
        textBody.css("font-size", savedSize + "px");
        $("#lbl-font-size").text(savedSize + "px");

        // Background Color Preset Theme
        var savedTheme = localStorage.getItem("reader-theme") || "dark";
        applyThemePreset(savedTheme);
    }

    // Font Family selector click
    $(".font-selector-btn").click(function() {
        var font = $(this).data("font");
        $(".font-selector-btn").removeClass("active");
        $(this).addClass("active");
        
        var fontFamilyVal = "'" + font + "', sans-serif";
        if (font === "Lora" || font === "Georgia") {
            fontFamilyVal = "'" + font + "', serif";
        } else if (font === "monospace") {
            fontFamilyVal = "monospace";
        }
        
        $("#reader-content-text-body").css("font-family", fontFamilyVal);
        localStorage.setItem("reader-font", font);
    });

    // Font Size Increase / Decrease
    $("#btn-font-increase").click(function() {
        var currentSize = parseInt($("#reader-content-text-body").css("font-size")) || 18;
        if (currentSize < 32) {
            var newSize = currentSize + 2;
            $("#reader-content-text-body").css("font-size", newSize + "px");
            $("#lbl-font-size").text(newSize + "px");
            localStorage.setItem("reader-size", newSize);
        }
    });

    $("#btn-font-decrease").click(function() {
        var currentSize = parseInt($("#reader-content-text-body").css("font-size")) || 18;
        if (currentSize > 12) {
            var newSize = currentSize - 2;
            $("#reader-content-text-body").css("font-size", newSize + "px");
            $("#lbl-font-size").text(newSize + "px");
            localStorage.setItem("reader-size", newSize);
        }
    });

    // Theme preset switcher click
    $(".theme-preset-btn").click(function() {
        var theme = $(this).data("theme");
        applyThemePreset(theme);
    });

    function applyThemePreset(theme) {
        $(".theme-preset-btn").removeClass("active");
        $(".theme-preset-btn[data-theme='" + theme + "']").addClass("active");
        
        // Reset classes
        $("body").removeClass("theme-light theme-sepia");
        
        if (theme === "light") {
            $("body").addClass("theme-light");
        } else if (theme === "sepia") {
            $("body").addClass("theme-sepia");
        } // "dark" doesn't require extra body class, uses default dark stylesheet
        
        localStorage.setItem("reader-theme", theme);
    }

    // --- Purchase Snow Flakes Event Listeners ---
    $(document).on("click", "#btn-navbar-balance", function(e) {
        e.preventDefault();
        var purchaseModal = new bootstrap.Modal(document.getElementById('purchaseFlakesModal'));
        purchaseModal.show();
    });

    var appliedCouponCode = null;
    var couponDiscountPercent = 0;

    // Helper to dynamically build a form and redirect to PayU checkout page
    function redirectToPayU(res) {
        var form = document.createElement("form");
        form.method = "POST";
        form.action = res.actionUrl;

        var fields = ["key", "txnid", "amount", "productinfo", "firstname", "email", "phone", "surl", "furl", "hash", "service_provider", "udf1", "udf2", "udf3", "udf4"];
        fields.forEach(function(field) {
            if (res[field] !== undefined) {
                var input = document.createElement("input");
                input.type = "hidden";
                input.name = field;
                input.value = res[field];
                form.appendChild(input);
            }
        });

        document.body.appendChild(form);
        form.submit();
    }

    // Helper to open Razorpay overlay checkout form
    function openRazorpayCheckout(res, btn, inputField) {
        var options = {
            "key": res.keyId,
            "amount": res.amount,
            "currency": res.currency,
            "name": res.name,
            "description": res.description,
            "order_id": res.orderId,
            "handler": function (response){
                btn.prop("disabled", true).html('<i class="fa fa-spinner fa-spin"></i> Verifying...');
                $.post("/api/payment/razorpay/verify", {
                    razorpay_payment_id: response.razorpay_payment_id,
                    razorpay_order_id: response.razorpay_order_id,
                    razorpay_signature: response.razorpay_signature,
                    udf1: res.udf1,
                    udf2: res.udf2,
                    udf3: res.udf3,
                    udf4: res.udf4
                }).done(function(verifyRes) {
                    showToast("Payment successful! " + res.description + " credited.");
                    $("#navbar-user-balance").text(verifyRes.newBalance);
                    
                    if (inputField) {
                        inputField.val('');
                        $("#custom-flakes-price-display").text("$0.00");
                    }
                    
                    var modalEl = document.getElementById('purchaseFlakesModal');
                    var modal = bootstrap.Modal.getInstance(modalEl);
                    if (modal) {
                        modal.hide();
                    }
                }).fail(function(verifyErr) {
                    var msg = verifyErr.responseJSON && verifyErr.responseJSON.error ? verifyErr.responseJSON.error : "Signature verification failed.";
                    showToast(msg, "error");
                }).always(function() {
                    if (inputField) {
                        btn.prop("disabled", false).html('<i class="fa-solid fa-credit-card me-2"></i>Purchase Custom');
                    } else {
                        btn.prop("disabled", false).text("Purchase");
                    }
                });
            },
            "prefill": {
                "name": res.prefillName,
                "email": res.prefillEmail,
                "contact": "9999999999"
            },
            "theme": {
                "color": "#1a1538"
            },
            "modal": {
                "ondismiss": function() {
                    if (inputField) {
                        btn.prop("disabled", false).html('<i class="fa-solid fa-credit-card me-2"></i>Purchase Custom');
                    } else {
                        btn.prop("disabled", false).text("Purchase");
                    }
                }
            }
        };
        try {
            var rzp1 = new Razorpay(options);
            rzp1.open();
        } catch (e) {
            console.error("Razorpay open failed:", e);
            showToast("Failed to initialize Razorpay payment.", "error");
        }
    }
    $(document).on("click", ".btn-purchase-pack", function(e) {
        e.preventDefault();
        var btn = $(this);
        var amount = btn.data("amount");
        var gatewayVal = $("input[name='paymentGateway']:checked").val();
        var gateway = (gatewayVal !== undefined) ? gatewayVal : "";
        var coupon = appliedCouponCode || ($("#coupon-code-input").length ? $("#coupon-code-input").val().toUpperCase().trim() : "");

        btn.prop("disabled", true).html('<i class="fa fa-spinner fa-spin"></i> Processing...');

        $.post("/api/user/purchase-flakes", { amount: amount, gateway: gateway, couponCode: coupon })
        .done(function(res) {
            if (res.payu) {
                redirectToPayU(res);
            } else if (res.razorpay) {
                openRazorpayCheckout(res, btn, null);
            } else {
                showToast(res.message);
                $("#navbar-user-balance").text(res.newBalance);
                
                // Close modal
                var modalEl = document.getElementById('purchaseFlakesModal');
                var modal = bootstrap.Modal.getInstance(modalEl);
                if (modal) {
                    modal.hide();
                }
                btn.prop("disabled", false).text("Purchase");
            }
        })
        .fail(function(err) {
            var msg = err.responseJSON && err.responseJSON.error ? err.responseJSON.error : "Failed to purchase Snow Flakes.";
            showToast(msg, "error");
            btn.prop("disabled", false).text("Purchase");
        });
    });

    // Custom Flakes dynamic pricing calculation
    function updateCustomFlakesPrice() {
        var input = $("#custom-flakes-input");
        var amount = parseInt(input.val());
        var display = $("#custom-flakes-price-display");
        
        if (isNaN(amount) || amount <= 0) {
            display.text("$0.00");
            return;
        }
        
        var packages = window.flakePackages || [];
        var price = 0.0;
        if (packages.length === 0) {
            // Default rate fallback if packages list is empty
            price = amount * 0.01;
        } else {
            // Sort ascending by amount
            var sortedPacks = [...packages].sort(function(a, b) {
                return a.amount - b.amount;
            });
            
            // Find closest package that is <= amount
            var applicablePack = sortedPacks[0];
            for (var i = 0; i < sortedPacks.length; i++) {
                if (sortedPacks[i].amount <= amount) {
                    applicablePack = sortedPacks[i];
                }
            }
            
            var rate = applicablePack.price / applicablePack.amount;
            price = amount * rate;
        }

        if (appliedCouponCode && couponDiscountPercent > 0) {
            var discounted = price * (1.0 - (couponDiscountPercent / 100.0));
            display.html('<span class="text-decoration-line-through text-muted fs-6" style="margin-right: 5px;">$' + price.toFixed(2) + '</span>$' + discounted.toFixed(2));
        } else {
            display.text("$" + price.toFixed(2));
        }
    }

    $(document).on("input change keyup", "#custom-flakes-input", function() {
        updateCustomFlakesPrice();
    });

    $(document).on("click", "#btn-purchase-custom-flakes", function(e) {
        e.preventDefault();
        var btn = $(this);
        var input = $("#custom-flakes-input");
        var amount = parseInt(input.val());
        
        if (isNaN(amount) || amount <= 0) {
            showToast("Please enter a valid amount of Snow Flakes.", "error");
            return;
        }
        
        var gatewayVal = $("input[name='paymentGateway']:checked").val();
        var gateway = (gatewayVal !== undefined) ? gatewayVal : "";
        var coupon = appliedCouponCode || ($("#coupon-code-input").length ? $("#coupon-code-input").val().toUpperCase().trim() : "");
        btn.prop("disabled", true).html('<i class="fa fa-spinner fa-spin me-2"></i>Processing...');
        
        $.post("/api/user/purchase-flakes", { amount: amount, gateway: gateway, couponCode: coupon })
        .done(function(res) {
            if (res.payu) {
                redirectToPayU(res);
            } else if (res.razorpay) {
                openRazorpayCheckout(res, btn, input);
            } else {
                showToast(res.message);
                $("#navbar-user-balance").text(res.newBalance);
                
                // Clear input
                input.val('');
                $("#custom-flakes-price-display").text("$0.00");
                
                // Close modal
                var modalEl = document.getElementById('purchaseFlakesModal');
                var modal = bootstrap.Modal.getInstance(modalEl);
                if (modal) {
                    modal.hide();
                }
                btn.prop("disabled", false).html('<i class="fa-solid fa-credit-card me-2"></i>Purchase Custom');
            }
        })
        .fail(function(err) {
            var msg = err.responseJSON && err.responseJSON.error ? err.responseJSON.error : "Failed to purchase Snow Flakes.";
            showToast(msg, "error");
            btn.prop("disabled", false).html('<i class="fa-solid fa-credit-card me-2"></i>Purchase Custom');
        });
    });

    // Apply Coupon Code Click Logic
    $(document).on("click", "#btn-apply-coupon", function(e) {
        e.preventDefault();
        var code = $("#coupon-code-input").val().toUpperCase().trim();
        var amount = parseInt($("#custom-flakes-input").val()) || 100;
        var msgDiv = $("#coupon-validation-msg");

        if (!code) {
            msgDiv.show().removeClass("text-success").addClass("text-danger").text("Please enter a coupon code.");
            return;
        }

        $.get("/api/user/validate-coupon", { code: code, amount: amount })
        .done(function(res) {
            if (res.valid) {
                appliedCouponCode = res.code;
                couponDiscountPercent = res.discountPercentage;
                msgDiv.show().removeClass("text-danger").addClass("text-success")
                    .text("Coupon '" + res.code + "' applied! " + res.discountPercentage + "% discount.");
                updateCustomFlakesPrice();
            }
        })
        .fail(function(err) {
            appliedCouponCode = null;
            couponDiscountPercent = 0;
            var errMsg = err.responseJSON && err.responseJSON.error ? err.responseJSON.error : "Invalid coupon code.";
            msgDiv.show().removeClass("text-success").addClass("text-danger").text(errMsg);
            updateCustomFlakesPrice();
        });
    });

    // Clear coupon selection on modal hidden
    $(document).ready(function() {
        var modalEl = document.getElementById('purchaseFlakesModal');
        if (modalEl) {
            modalEl.addEventListener('hidden.bs.modal', function () {
                $("#coupon-code-input").val("");
                $("#coupon-validation-msg").hide().text("");
                appliedCouponCode = null;
                couponDiscountPercent = 0;
                $("#custom-flakes-price-display").text("$0.00");
            });
        }
    });

    // Toggle feature/highlight novel
    $(document).on("click", ".btn-toggle-feature", function(e) {
        e.preventDefault();
        var btn = $(this);
        var novelId = btn.data("id");

        btn.prop("disabled", true);

        $.post("/api/admin/novels/" + novelId + "/feature")
        .done(function(res) {
            showToast(res.message);
            setTimeout(function() {
                location.reload();
            }, 800);
        })
        .fail(function(err) {
            var msg = err.responseJSON && err.responseJSON.error ? err.responseJSON.error : "Failed to feature story.";
            showToast(msg, "error");
            btn.prop("disabled", false);
        });
    });



    // Autocomplete for Genres in Admin panel
    const ALL_GENRES = [
        "Action", "Adventure", "Apocalypse", "Beast World", "BL (Boys Love)", 
        "GL (Girls Love)", "Comedy", "Comic", "Cultivation", "Cyberpunk", 
        "Doujinshi", "Drama", "Ecchi", "Family", "Fantasy", "Game", 
        "Gekiga", "Gender Bender", "Guilds", "Harem", "High Fantasy", 
        "Historical", "Horror", "Isekai", "Josei", "Light Novel", 
        "LitRPG", "Magic", "Manga", "Manhua", "Manhwa", "Martial Arts", 
        "Mecha", "Military", "Modern", "Music", "Mystery", "Parody", 
        "Police", "Post-Apocalyptic", "Psychological", "Rebirth", 
        "Reincarnation", "Reverse Harem", "Romance", "Samurai", "School Life", 
        "Sci-Fi", "Seinen", "Shoujo", "Shoujo Ai", "Shounen", "Shounen Ai", 
        "Slice of Life", "Smut", "Space Opera", "Sports", "Steampunk", 
        "Super Power", "Supernatural", "Survival", "System", "Thriller", 
        "Time Travel", "Tower Climbing", "Tragedy", "Transmigration", 
        "Urban Fantasy", "Vampire", "Villainess", "Virtual Reality", 
        "Webtoon", "Werewolf", "Wuxia", "Xianxia", "Xuanhuan", "Yaoi", 
        "Yuri", "Zombie"
    ];

    const genreInput = $("#story-genre");
    const genreDropdown = $("#genre-autocomplete-dropdown");

    if (genreInput.length > 0 && genreDropdown.length > 0) {
        genreInput.on("input focus", function() {
            var val = $(this).val();
            var parts = val.split(",");
            var currentQuery = parts[parts.length - 1].trim().toLowerCase();

            if (currentQuery.length >= 1) {
                // Find already selected genres to exclude them
                var selectedGenres = parts.slice(0, parts.length - 1).map(g => g.trim().toLowerCase());
                
                var matches = ALL_GENRES.filter(function(genre) {
                    return genre.toLowerCase().includes(currentQuery) && !selectedGenres.includes(genre.toLowerCase());
                });

                if (matches.length > 0) {
                    genreDropdown.empty();
                    matches.forEach(function(genre) {
                        genreDropdown.append('<div class="genre-suggestion-item" data-value="' + genre + '">' + genre + '</div>');
                    });
                    genreDropdown.removeClass("d-none");
                } else {
                    genreDropdown.addClass("d-none");
                }
            } else {
                genreDropdown.addClass("d-none");
            }
        });

        // Click on suggestion
        genreDropdown.on("click", ".genre-suggestion-item", function() {
            var selectedVal = $(this).data("value");
            var val = genreInput.val();
            var parts = val.split(",");
            parts[parts.length - 1] = " " + selectedVal;
            
            // Clean up empty elements and format nicely
            var newVal = parts.map(p => p.trim()).filter(p => p.length > 0).join(", ") + ", ";
            genreInput.val(newVal);
            genreInput.focus();
            genreDropdown.addClass("d-none");
        });

        // Hide suggestions on clicking outside
        $(document).click(function(e) {
            if (!genreInput.is(e.target) && !genreDropdown.is(e.target) && genreDropdown.has(e.target).length === 0) {
                genreDropdown.addClass("d-none");
            }
        });
    }

});